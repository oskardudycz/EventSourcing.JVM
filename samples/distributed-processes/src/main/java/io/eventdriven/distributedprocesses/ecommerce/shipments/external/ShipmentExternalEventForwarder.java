package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentSummary;

import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.*;

public class ShipmentExternalEventForwarder {
  private final ShipmentSummaries shipmentSummaries;
  private final IntegrationEventBus eventBus;

  public ShipmentExternalEventForwarder(
    ShipmentSummaries shipmentSummaries,
    IntegrationEventBus eventBus
  ) {
    this.shipmentSummaries = shipmentSummaries;
    this.eventBus = eventBus;
  }

  public void on(ShipmentEvent.StockReserved event) {
    eventBus.publish(new ShipmentExternalEvent.StockReserved(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.reservedAt(),
      event.reservedUntil()
    ));
  }

  public void on(ShipmentEvent.PackageWasSent event) {
    eventBus.publish(new ShipmentExternalEvent.PackageWasSent(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.sentAt()
    ));
  }

  public void on(ShipmentEvent.PackageWasDelivered event) {
    eventBus.publish(new ShipmentExternalEvent.PackageWasDelivered(
      event.shipmentId(),
      event.referenceId(),
      event.deliveredAt()
    ));
  }

  public void on(ProductWasOutOfStock event) {
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
  public void on(StockReservationExpired event) {
    publishFailure(
      event.shipmentId(),
      event.referenceId(),
      get(event.shipmentId()).getProductItems(),
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
    eventBus.publish(new StockReservationFailed(
      shipmentId,
      referenceId,
      productItems,
      reason,
      failedAt
    ));
  }

  private ShipmentSummary get(ShipmentId shipmentId) {
    return shipmentSummaries.get(shipmentId)
      .orElseThrow(() -> new IllegalStateException("Cannot enrich event, as shipment with id '%s' was not found".formatted(shipmentId)));
  }
}
