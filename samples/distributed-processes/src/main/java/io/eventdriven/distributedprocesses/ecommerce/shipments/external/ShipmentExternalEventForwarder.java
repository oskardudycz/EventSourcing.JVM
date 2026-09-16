package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;

public class ShipmentExternalEventForwarder {
  private final IntegrationEventBus eventBus;

  public ShipmentExternalEventForwarder(IntegrationEventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void on(ShipmentEvent.StockReserved event) {
    eventBus.publish(new ShipmentExternalEvent.StockReserved(
      event.shipmentId(),
      event.referenceId(),
      event.reservedAt(),
      event.reservedUntil()
    ));
  }

  public void on(ShipmentEvent.ProductWasOutOfStock event) {
    eventBus.publish(new ShipmentExternalEvent.ProductWasOutOfStock(
      event.shipmentId(),
      event.referenceId(),
      event.productItems(),
      event.availabilityCheckedAt()
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

  public void on(ShipmentEvent.StockReservationExpired event) {
    eventBus.publish(new ShipmentExternalEvent.StockReservationExpired(
      event.shipmentId(),
      event.referenceId(),
      event.expiredAt()
    ));
  }
}
