package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;

import java.util.Optional;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

// Everything the outside world is told about a shipment, kept where the forwarder can read it
// without loading the aggregate. Handlers are named per event, as EventEnvelope<PackageWasSent>
// and EventEnvelope<StockReleased> erase to the same overload.
public class ShipmentSummaries {
  private final Database database;

  public ShipmentSummaries(Database database) {
    this.database = database;
  }

  public void onStockReserved(EventEnvelope<StockReserved> event) {
    database.store(
      ShipmentSummary.class,
      event.data().shipmentId(),
      event.metadata().streamPosition(),
      new ShipmentSummary(
        event.data().shipmentId(),
        event.data().referenceId(),
        event.data().productItems(),
        ShipmentSummary.Status.Reserved,
        event.data().reservedAt(),
        event.data().reservedUntil()
      )
    );
  }

  public void onProductWasOutOfStock(EventEnvelope<ProductWasOutOfStock> event) {
    database.store(
      ShipmentSummary.class,
      event.data().shipmentId(),
      event.metadata().streamPosition(),
      new ShipmentSummary(
        event.data().shipmentId(),
        event.data().referenceId(),
        event.data().productItems(),
        ShipmentSummary.Status.OutOfStock,
        null,
        null
      )
    );
  }

  public void onPackageWasSent(EventEnvelope<PackageWasSent> event) {
    setStatus(event, event.data().shipmentId(), ShipmentSummary.Status.Sent);
  }

  public void onPackageWasDelivered(EventEnvelope<PackageWasDelivered> event) {
    setStatus(event, event.data().shipmentId(), ShipmentSummary.Status.Delivered);
  }

  public void onStockReleased(EventEnvelope<StockReleased> event) {
    setStatus(event, event.data().shipmentId(), ShipmentSummary.Status.Released);
  }

  public void onStockReservationExpired(EventEnvelope<StockReservationExpired> event) {
    setStatus(event, event.data().shipmentId(), ShipmentSummary.Status.Expired);
  }

  public Optional<ShipmentSummary> get(ShipmentId shipmentId) {
    return database.get(ShipmentSummary.class, shipmentId);
  }

  private <Event> void setStatus(
    EventEnvelope<Event> event,
    ShipmentId shipmentId,
    ShipmentSummary.Status status
  ) {
    database.getAndUpdate(
      ShipmentSummary.class, shipmentId, event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(status);

        return summary;
      });
  }
}
