package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

// Reservations still waiting for their deadline. A shipment leaves the view the moment
// it is sent, released or expired, so only what can still time out is kept.
public class StockReservations {
  private final Database database;

  public StockReservations(Database database) {
    this.database = database;
  }

  public void onStockReserved(EventEnvelope<StockReserved> event) {
    database.store(
      StockReservation.class,
      event.data().shipmentId(),
      event.metadata().streamPosition(),
      new StockReservation(event.data().shipmentId(), event.data().reservedUntil())
    );
  }

  public void onPackageWasSent(EventEnvelope<PackageWasSent> event) {
    database.delete(StockReservation.class, event.data().shipmentId());
  }

  public void onStockReleased(EventEnvelope<StockReleased> event) {
    database.delete(StockReservation.class, event.data().shipmentId());
  }

  public void onStockReservationExpired(EventEnvelope<StockReservationExpired> event) {
    database.delete(StockReservation.class, event.data().shipmentId());
  }

  public List<ShipmentId> expiredAt(OffsetDateTime now) {
    return database
      .find(StockReservation.class, reservation -> !reservation.getReservedUntil().isAfter(now))
      .stream()
      .map(StockReservation::getShipmentId)
      .toList();
  }
}
