package io.eventdriven.distributedprocesses.ecommerce.shipments.reservation;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.time.OffsetDateTime;
import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ExpireStockReservation;

public class ReservationExpiryWorker {
  private final StockReservations stockReservations;
  private final MessageBus messageBus;

  public ReservationExpiryWorker(
    StockReservations stockReservations,
    MessageBus messageBus
  ) {
    this.stockReservations = stockReservations;
    this.messageBus = messageBus;
  }

  // Production would call this on a schedule; tests call it directly to keep time explicit.
  public void run(OffsetDateTime now) {
    for (var shipmentId : stockReservations.expiredAt(now)) {
      messageBus.publish(List.of(new ExpireStockReservation(shipmentId, now)));
    }
  }
}
