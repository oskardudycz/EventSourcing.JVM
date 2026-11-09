package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// Stands in for a provider that took the booking but has not answered yet.
public class SilentDeliveryProvider implements DeliveryProvider {
  @Override
  public void book(ShipmentId shipmentId, ProductItem[] productItems) {
  }
}
