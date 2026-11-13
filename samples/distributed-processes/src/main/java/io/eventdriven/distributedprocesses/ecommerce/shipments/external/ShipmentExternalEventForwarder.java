package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;

import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.*;

public class ShipmentExternalEventForwarder {
  private final MessageBus messageBus;

  public ShipmentExternalEventForwarder(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  public void onStockReserved(EventEnvelope<ShipmentEvent.StockReserved> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.StockReserved(
      event.shipmentId(),
      event.referenceId(),
      event.reservedAt()
    )));
  }

  public void onPackageWasSent(EventEnvelope<ShipmentEvent.PackageWasSent> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.PackageWasSent(
      event.shipmentId(),
      event.referenceId(),
      event.carrier(),
      event.trackingNumber(),
      event.sentAt()
    )));
  }

  public void onProductWasOutOfStock(EventEnvelope<ProductWasOutOfStock> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentFailed(
      event.shipmentId(),
      event.referenceId(),
      ShipmentFailed.Reason.ProductsOutOfStock,
      event.availabilityCheckedAt()
    )));
  }

  public void onFulfillmentRejected(EventEnvelope<ShipmentEvent.FulfillmentRejected> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentFailed(
      event.shipmentId(),
      event.referenceId(),
      ShipmentFailed.Reason.FulfillmentRejected,
      event.rejectedAt()
    )));
  }
}
