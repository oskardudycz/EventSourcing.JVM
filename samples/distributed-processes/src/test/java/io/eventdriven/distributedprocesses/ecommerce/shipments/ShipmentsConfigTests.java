package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.CallingBackDeliveryProvider;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.DeliveryProvider;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.SilentDeliveryProvider;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

// The wiring in ShipmentsConfig is load bearing: the forwarder reads the summary projection.
public class ShipmentsConfigTests {
  private static final Duration reservationValidity = Duration.ofHours(2);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiredAt = now.plus(reservationValidity);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  @Test
  public void expiryIsForwardedWithTheItemsTheSummaryProjectionKept() {
    configure(new SilentDeliveryProvider());
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    externalEvents.reset();

    messageBus.publish(List.of(new ExpireStockReservation(shipmentId, expiredAt)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReservationFailed(
        shipmentId,
        referenceId,
        available,
        ShipmentExternalEvent.StockReservationFailed.Reason.ReservationExpired,
        expiredAt
      )
    );
  }

  @Test
  public void runningOutOfStockIsForwardedWithoutWaitingForItsOwnProjection() {
    configure(new SilentDeliveryProvider());
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
  public void dispatchIsForwardedWithTheTrackingBeforeTheDeliveryTheProviderReports() {
    var deliveryProvider = new CallingBackDeliveryProvider(messageBus);
    configure(deliveryProvider);
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    externalEvents.reset();

    messageBus.publish(List.of(new SendPackage(shipmentId)));
    deliveryProvider.confirmBookings();
    deliveryProvider.deliverParcels();

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasSent(
        shipmentId,
        referenceId,
        available,
        CallingBackDeliveryProvider.carrier,
        CallingBackDeliveryProvider.trackingNumber,
        now
      ),
      new ShipmentExternalEvent.PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  private void configure(DeliveryProvider deliveryProvider) {
    messageBus.use(message -> {
      if (message instanceof ShipmentExternalEvent)
        externalEvents.catchMessage(message);
    });

    ShipmentsConfig.configure(
      eventStore,
      messageBus,
      deliveryProvider,
      isProductAvailable,
      reservationValidity,
      () -> now
    );
  }
}
