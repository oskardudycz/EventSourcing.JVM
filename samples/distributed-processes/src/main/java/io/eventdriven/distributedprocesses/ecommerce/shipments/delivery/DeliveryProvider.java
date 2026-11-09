package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// A real provider would only accept the booking here and answer through webhooks: first with the
// carrier and tracking number, later with the delivery confirmation. In this sample that is
// modelled by the implementation sending ConfirmParcelBooking and DeliverPackage back.
public interface DeliveryProvider {
  void book(ShipmentId shipmentId, ProductItem[] productItems);
}
