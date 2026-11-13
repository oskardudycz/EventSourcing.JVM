package io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// A real fulfillment service only takes the request here and answers later: that it sent the
// package, with the carrier and tracking number, or that it rejects the request; then that the
// package was delivered. In this sample the implementation answers by sending ConfirmPackageSent,
// RejectFulfillment and DeliverPackage.
public interface FulfillmentService {
  void request(ShipmentId shipmentId, ProductItem[] productItems);
}
