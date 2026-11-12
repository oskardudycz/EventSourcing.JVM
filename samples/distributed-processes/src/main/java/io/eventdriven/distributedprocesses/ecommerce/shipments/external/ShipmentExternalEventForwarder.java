package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import java.time.OffsetDateTime;
import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.*;

public class ShipmentExternalEventForwarder {
  private final ShipmentSummaries shipmentSummaries;
  private final MessageBus messageBus;

  public ShipmentExternalEventForwarder(
    ShipmentSummaries shipmentSummaries,
    MessageBus messageBus
  ) {
    this.shipmentSummaries = shipmentSummaries;
    this.messageBus = messageBus;
  }

  public void onStockReserved(EventEnvelope<ShipmentEvent.StockReserved> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.StockReserved(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.reservedAt(),
      event.reservedUntil()
    )));
  }

  public void onPackageWasSent(EventEnvelope<ShipmentEvent.PackageWasSent> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.PackageWasSent(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.carrier(),
      event.trackingNumber(),
      event.sentAt()
    )));
  }

  public void onPackageWasDelivered(EventEnvelope<ShipmentEvent.PackageWasDelivered> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.PackageWasDelivered(
      event.shipmentId(),
      event.referenceId(),
      event.deliveredAt()
    )));
  }

  public void onProductWasOutOfStock(EventEnvelope<ProductWasOutOfStock> envelope) {
    var event = envelope.data();
    publishFailure(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.availabilityCheckedAt(),
      StockReservationFailed.Reason.ProductsOutOfStock
    );
  }

  // The expiry only knows which shipment ran out of time, so the items it reserved
  // come from the summary the reservation left behind.
  public void onStockReservationExpired(EventEnvelope<StockReservationExpired> envelope) {
    var event = envelope.data();
    publishFailure(
      event.shipmentId(),
      event.referenceId(),
      get(event.shipmentId(), envelope.metadata().streamPosition()).getProductItems(),
      event.expiredAt(),
      StockReservationFailed.Reason.ReservationExpired
    );
  }

  private void publishFailure(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime failedAt,
    StockReservationFailed.Reason reason
  ) {
    messageBus.publish(List.of(new StockReservationFailed(
      shipmentId,
      referenceId,
      productItems,
      reason,
      failedAt
    )));
  }

  private ShipmentSummary get(ShipmentId shipmentId, long streamPosition) {
    return shipmentSummaries.get(shipmentId)
      .filter(summary -> summary.getVersion() >= streamPosition)
      .orElseThrow(() -> new IllegalStateException(
        "Summary of shipment '%s' has not reached position %d yet".formatted(shipmentId.value(), streamPosition)
      ));
  }
}
