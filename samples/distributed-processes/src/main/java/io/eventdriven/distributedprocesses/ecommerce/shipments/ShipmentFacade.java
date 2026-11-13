package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

public class ShipmentFacade {
  private final AggregateStore<Shipment, ShipmentEvent, ShipmentId> store;
  private final Function<ProductItem, Boolean> isProductAvailable;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(ShipmentId shipmentId) {
    return "Shipment-%s".formatted(shipmentId.tail());
  }

  public ShipmentFacade(
    AggregateStore<Shipment, ShipmentEvent, ShipmentId> store,
    Function<ProductItem, Boolean> isProductAvailable,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.isProductAvailable = isProductAvailable;
    this.now = now;
  }

  public void reserveStock(ReserveStock command) {
    var shipmentId = ShipmentId.derivedFrom(command.referenceId());
    store.getAndUpdate(
      shipmentId,
      current -> current.reserveStock(
        isProductAvailable,
        shipmentId,
        command.referenceId(),
        command.productItems(),
        now.get()
      )
    );
  }

  public void requestFulfillment(RequestFulfillment command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.requestFulfillment(now.get())
    );
  }

  public void confirmPackageSent(ConfirmPackageSent command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.confirmSent(command.carrier(), command.trackingNumber(), now.get())
    );
  }

  public void rejectFulfillment(RejectFulfillment command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.rejectFulfillment(now.get())
    );
  }

  public void deliverPackage(DeliverPackage command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.deliver(now.get())
    );
  }

  public void releaseStock(ReleaseStock command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.releaseStock(now.get())
    );
  }
}
