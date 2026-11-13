package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// Stands in for a fulfillment service that took the request but has not answered yet.
public class SilentFulfillmentService implements FulfillmentService {
  @Override
  public void request(ShipmentId shipmentId, ProductItem[] productItems) {
  }
}
