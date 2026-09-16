package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

public class ShipmentFacade {
  private final AggregateStore<Shipment, ShipmentEvent, ShipmentId> store;
  private final Function<ProductItem, Boolean> isProductAvailable;
  private final Duration reservationValidity;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(ShipmentId shipmentId) {
    return "Shipment-%s".formatted(shipmentId.tail());
  }

  public ShipmentFacade(
    AggregateStore<Shipment, ShipmentEvent, ShipmentId> store,
    Function<ProductItem, Boolean> isProductAvailable,
    Duration reservationValidity,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.isProductAvailable = isProductAvailable;
    this.reservationValidity = reservationValidity;
    this.now = now;
  }

  public void reserveStock(ReserveStock command) {
    var shipmentId = ShipmentId.derivedFrom(command.referenceId());
    var reservedAt = now.get();

    store.getAndUpdate(
      shipmentId,
      current -> current.reserveStock(
        isProductAvailable,
        shipmentId,
        command.referenceId(),
        command.productItems(),
        reservedAt,
        reservedAt.plus(reservationValidity)
      )
    );
  }

  public void sendPackage(SendPackage command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.send(now.get())
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

  public void expireStockReservation(ExpireStockReservation command) {
    store.getAndUpdate(
      command.shipmentId(),
      current -> current.expireReservation(command.expiredAt())
    );
  }
}
