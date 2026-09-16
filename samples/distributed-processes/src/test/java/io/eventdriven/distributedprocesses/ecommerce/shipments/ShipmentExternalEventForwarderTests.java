package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

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

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public ShipmentExternalEventForwarderTests() {
    integrationEventBus.use(externalEvents::catchMessage);

    ShipmentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      integrationEventBus,
      new SilentDeliveryProvider(),
      isProductAvailable,
      reservationValidity,
      () -> now
    );
  }

  @Test
  public void reservingForwardsStockReservedWithItsDeadline() {
    commandBus.send(new ReserveStock(referenceId, available));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReserved(shipmentId, referenceId, now, reservedUntil)
    );
  }

  @Test
  public void sendingForwardsPackageWasSent() {
    reserve();

    commandBus.send(new SendPackage(shipmentId));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasSent(shipmentId, referenceId, available, now)
    );
  }

  @Test
  public void deliveringForwardsPackageWasDelivered() {
    reserve();
    commandBus.send(new SendPackage(shipmentId));
    externalEvents.reset();

    commandBus.send(new DeliverPackage(shipmentId));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasDelivered(shipmentId, referenceId, now)
    );
  }

  @Test
  public void runningOutOfStockForwardsProductWasOutOfStock() {
    var productItems = new ProductItem[]{availableItem, unavailableItem};

    commandBus.send(new ReserveStock(referenceId, productItems));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.ProductWasOutOfStock(shipmentId, referenceId, productItems, now)
    );
  }

  @Test
  public void expiringForwardsStockReservationExpiredSoTheOrderIsNeverLeftWaiting() {
    reserve();

    commandBus.send(new ExpireStockReservation(shipmentId, reservedUntil));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReservationExpired(shipmentId, referenceId, reservedUntil)
    );
  }

  @Test
  public void releasingForwardsNothingBecauseNobodyWaitsOnIt() {
    reserve();

    commandBus.send(new ReleaseStock(shipmentId));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void reserve() {
    commandBus.send(new ReserveStock(referenceId, available));
    externalEvents.reset();
  }
}
