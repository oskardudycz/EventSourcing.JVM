package io.eventdriven.distributedprocesses.ecommerce.shipments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.PackageWasSent;

public class DeliveryProviderClient {
  private static final Logger logger = LoggerFactory.getLogger(DeliveryProviderClient.class);

  private final DeliveryProvider deliveryProvider;

  public DeliveryProviderClient(DeliveryProvider deliveryProvider) {
    this.deliveryProvider = deliveryProvider;
  }

  public void on(PackageWasSent event) {
    try {
      deliveryProvider.deliver(event.shipmentId(), event.productItems());
    } catch (Exception failedHandOver) {
      // The process has no "delivery failed" event, so there is nothing to record here:
      // the shipment stays sent and undelivered until someone hands it over again.
      logger.error(
        "Handing shipment %s over to the delivery provider failed".formatted(event.shipmentId()),
        failedHandOver
      );
    }
  }
}
