package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// Stands in for a provider that cannot be reached at all.
public class ThrowingDeliveryProvider implements DeliveryProvider {
  @Override
  public void book(ShipmentId shipmentId, ProductItem[] productItems) {
    throw new RuntimeException("Delivery provider is unreachable");
  }
}
