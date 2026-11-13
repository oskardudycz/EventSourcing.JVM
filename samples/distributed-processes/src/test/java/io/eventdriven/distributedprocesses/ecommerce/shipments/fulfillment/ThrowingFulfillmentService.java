package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// Stands in for a fulfillment service that cannot be reached at all.
public class ThrowingFulfillmentService implements FulfillmentService {
  @Override
  public void request(ShipmentId shipmentId, ProductItem[] productItems) {
    throw new RuntimeException("Fulfillment service is unreachable");
  }
}
