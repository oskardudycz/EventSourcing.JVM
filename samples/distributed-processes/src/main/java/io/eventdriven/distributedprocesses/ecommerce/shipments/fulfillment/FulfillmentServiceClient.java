package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.FulfillmentRequested;

public class FulfillmentServiceClient {
  private static final Logger logger = LoggerFactory.getLogger(FulfillmentServiceClient.class);

  private final FulfillmentService fulfillmentService;

  public FulfillmentServiceClient(FulfillmentService fulfillmentService) {
    this.fulfillmentService = fulfillmentService;
  }

  public void on(FulfillmentRequested event) {
    try {
      fulfillmentService.request(event.shipmentId(), event.productItems());
    } catch (Exception failedRequest) {
      // Nothing is recorded here: the order is cancelled when its payment authorization expires.
      logger.error(
        "Requesting fulfillment for shipment %s failed".formatted(event.shipmentId()),
        failedRequest
      );
    }
  }
}
