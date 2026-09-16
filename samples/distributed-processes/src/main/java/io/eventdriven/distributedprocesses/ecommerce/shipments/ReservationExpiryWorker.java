package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;

import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ExpireStockReservation;

public class ReservationExpiryWorker {
  private final StockReservations stockReservations;
  private final CommandBus commandBus;

  public ReservationExpiryWorker(
    StockReservations stockReservations,
    CommandBus commandBus
  ) {
    this.stockReservations = stockReservations;
    this.commandBus = commandBus;
  }

  // Production would call this on a schedule; tests call it directly to keep time explicit.
  public void run(OffsetDateTime now) {
    for (var shipmentId : stockReservations.expiredAt(now)) {
      commandBus.send(new ExpireStockReservation(shipmentId, now));
    }
  }
}
