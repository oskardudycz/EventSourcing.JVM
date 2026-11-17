package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
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

  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.responses, payloadType = ShipmentExternalEvent.StockReserved.class, description = "Tells the requester that its products are set aside."))
  public void onStockReserved(EventEnvelope<ShipmentEvent.StockReserved> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentExternalEvent.StockReserved(
      event.shipmentId(),
      event.referenceId(),
      event.reservedAt()
    )));
  }

  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.responses, payloadType = ShipmentExternalEvent.PackageWasSent.class, description = "Tells the requester that its package was sent."))
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

  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.responses, payloadType = ShipmentExternalEvent.ShipmentFailed.class, description = "Tells the requester that its products cannot ship."))
  public void onProductWasOutOfStock(EventEnvelope<ProductWasOutOfStock> envelope) {
    var event = envelope.data();
    messageBus.publish(List.of(new ShipmentFailed(
      event.shipmentId(),
      event.referenceId(),
      ShipmentFailed.Reason.ProductsOutOfStock,
      event.availabilityCheckedAt()
    )));
  }

  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.responses, payloadType = ShipmentExternalEvent.ShipmentFailed.class, description = "Tells the requester that its products cannot ship."))
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
