package io.eventdriven.distributedprocesses.ecommerce.shipments;


// A real provider would only accept the parcel here and call back with the delivery
// confirmation later, which is why delivering is a separate command rather than a return value.
public interface DeliveryProvider {
  void deliver(ShipmentId shipmentId, ProductItem[] productItems);
}
