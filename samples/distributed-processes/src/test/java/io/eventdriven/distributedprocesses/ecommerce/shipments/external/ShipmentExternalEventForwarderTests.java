package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.SilentFulfillmentService;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

public class ShipmentExternalEventForwarderTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
  private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
  private final ProductItem[] available = new ProductItem[]{availableItem};
  private final Function<ProductItem, Boolean> isProductAvailable =
    item -> Set.of(availableItem).contains(item);
  private final OffsetDateTime now = OffsetDateTime.now();
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
      new SilentFulfillmentService(),
      isProductAvailable,
      () -> now
    );
  }

  @Test
  public void reservingForwardsStockReserved() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.StockReserved(shipmentId, referenceId, now)
    );
  }

  @Test
  public void sendingForwardsPackageWasSentWithTheTracking() {
    requestFulfillment();

    messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.PackageWasSent(shipmentId, referenceId, carrier, trackingNumber, now)
    );
  }

  @Test
  public void deliveringForwardsNothingBecauseNobodyWaitsOnIt() {
    requestFulfillment();
    messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));
    externalEvents.reset();

    messageBus.publish(List.of(new DeliverPackage(shipmentId)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningOutOfStockForwardsShipmentFailed() {
    messageBus.publish(List.of(new ReserveStock(referenceId, new ProductItem[]{availableItem, unavailableItem})));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.ShipmentFailed(
        shipmentId,
        referenceId,
        ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock,
        now
      )
    );
  }

  @Test
  public void rejectingTheFulfillmentForwardsShipmentFailed() {
    requestFulfillment();

    messageBus.publish(List.of(new RejectFulfillment(shipmentId)));

    externalEvents.shouldReceiveMessages(
      new ShipmentExternalEvent.ShipmentFailed(
        shipmentId,
        referenceId,
        ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected,
        now
      )
    );
  }

  @Test
  public void releasingForwardsNothingBecauseNobodyWaitsOnIt() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    externalEvents.reset();

    messageBus.publish(List.of(new ReleaseStock(shipmentId)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void requestFulfillment() {
    messageBus.publish(List.of(new ReserveStock(referenceId, available)));
    messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
    externalEvents.reset();
  }
}
