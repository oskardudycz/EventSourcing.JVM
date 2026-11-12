package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.SilentDeliveryProvider;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentFacade;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShipmentExternalEventForwarderTests {
  private static final Duration reservationValidity = Duration.ofHours(2);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = now.plus(reservationValidity);
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public ShipmentExternalEventForwarderTests() {
    messageBus.use(message -> {
      if (message instanceof ShipmentExternalEvent)
        externalEvents.catchMessage(message);
    });

    ShipmentsConfig.configure(
      eventStore,
      messageBus,
      new SilentDeliveryProvider(),
      isProductAvailable,
      reservationValidity,
      () -> now
    );
  }

  @Test
  public void reservingForwardsStockReservedWithItsDeadline() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReserved(shipmentId, referenceId, available, now, reservedUntil)
    );
  }

  @Test
  public void sendingForwardsPackageWasSentWithTheTracking() {
    reserve();

    messageBus.publish(List.of(new SendPackage(shipmentId)));
    messageBus.publish(List.of(new ConfirmParcelBooking(shipmentId, carrier, trackingNumber)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasSent(
        shipmentId,
        referenceId,
        available,
        carrier,
        trackingNumber,
        now
      )
    );
  }

  @Test
  public void deliveringForwardsPackageWasDelivered() {
    reserve();
    messageBus.publish(List.of(new SendPackage(shipmentId)));
    messageBus.publish(List.of(new ConfirmParcelBooking(shipmentId, carrier, trackingNumber)));
    externalEvents.reset();

    messageBus.publish(List.of(new DeliverPackage(shipmentId)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  @Test
  public void runningOutOfStockForwardsStockReservationFailed() {
    var productItems = new ProductItem[]{availableItem, unavailableItem};

    messageBus.publish(List.of(new ReserveStock(referenceId, productItems)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReservationFailed(
        shipmentId,
        referenceId,
        productItems,
        ShipmentExternalEvent.StockReservationFailed.Reason.ProductsOutOfStock,
        now
      )
    );
  }

  @Test
  public void expiringForwardsStockReservationFailedSoTheOrderIsNeverLeftWaiting() {
    reserve();

    messageBus.publish(List.of(new ExpireStockReservation(shipmentId, reservedUntil)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReservationFailed(
        shipmentId,
        referenceId,
        available,
        ShipmentExternalEvent.StockReservationFailed.Reason.ReservationExpired,
        reservedUntil
      )
    );
  }

  @Test
  public void releasingForwardsNothingBecauseNobodyWaitsOnIt() {
    reserve();

    messageBus.publish(List.of(new ReleaseStock(shipmentId)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void reserve() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    externalEvents.reset();
  }

  @Test
  public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
    var summaries = new ShipmentSummaries(new Database());
    var bus = new InMemoryMessageBus();
    var published = new MessageCatcher();
    bus.use(published::catchMessage);
    var forwarder = new ShipmentExternalEventForwarder(summaries, bus);
    var streamId = ShipmentFacade.mapToStreamId(shipmentId);

    summaries.onStockReserved(new EventEnvelope<>(
      new ShipmentEvent.StockReserved(shipmentId, referenceId, available, now, reservedUntil),
      new EventMetadata(streamId, 0, 0)
    ));
    var expired = new EventEnvelope<>(
      new ShipmentEvent.StockReservationExpired(shipmentId, referenceId, reservedUntil),
      new EventMetadata(streamId, 1, 1)
    );

    assertThatThrownBy(() -> forwarder.onStockReservationExpired(expired)).isInstanceOf(IllegalStateException.class);
    published.shouldNotReceiveAnyEvent();

    summaries.onStockReservationExpired(expired);
    forwarder.onStockReservationExpired(expired);

    published.shouldReceiveMessages(new ShipmentExternalEvent.StockReservationFailed(
      shipmentId,
      referenceId,
      available,
      ShipmentExternalEvent.StockReservationFailed.Reason.ReservationExpired,
      reservedUntil
    ));
  }
}
