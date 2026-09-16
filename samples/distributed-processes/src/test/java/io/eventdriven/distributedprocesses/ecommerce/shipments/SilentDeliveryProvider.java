package io.eventdriven.distributedprocesses.ecommerce.shipments;


// Stands in for a provider that has taken the parcel but not delivered it yet.
public class SilentDeliveryProvider implements DeliveryProvider {
  @Override
  public void deliver(ShipmentId shipmentId, ProductItem[] productItems) {
  }
}
