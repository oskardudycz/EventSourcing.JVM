package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// A real provider books the parcel and returns the carrier and tracking number at once, as
// label APIs do, but only calls back with the delivery confirmation later, which is why
// delivering is a separate command rather than a return value.
public interface DeliveryProvider {
  ParcelBooking book(ShipmentId shipmentId, ProductItem[] productItems);
}
