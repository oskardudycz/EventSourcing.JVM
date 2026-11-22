package io.eventdriven.ecommerce.shipments.fulfillment;

import io.eventdriven.ecommerce.shipments.Shipment;

// Stands in for a fulfillment service that took the request but has not answered yet.
public class SilentFulfillmentService implements FulfillmentService {
    @Override
    public void request(Shipment.Id shipmentId, Shipment.ProductItem[] productItems) {}
}
