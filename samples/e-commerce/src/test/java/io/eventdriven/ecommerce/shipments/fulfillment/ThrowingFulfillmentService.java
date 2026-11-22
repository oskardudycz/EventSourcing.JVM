package io.eventdriven.ecommerce.shipments.fulfillment;

import io.eventdriven.ecommerce.shipments.Shipment;

// Stands in for a fulfillment service that cannot be reached at all.
public class ThrowingFulfillmentService implements FulfillmentService {
    @Override
    public void request(Shipment.Id shipmentId, Shipment.ProductItem[] productItems) {
        throw new RuntimeException("Fulfillment service is unreachable");
    }
}
