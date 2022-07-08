package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.PackageWasSent;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.ProductWasOutOfStock;

public class Shipment extends AbstractAggregate<ShipmentEvent, UUID> {
  enum Status {
    Sent,
    ProductsOutOfStock
  }

  private UUID orderId;
  private ProductItem[] productItems;
  private Status status;

  public Shipment(Function<ProductItem, Boolean> isProductAvailable, UUID shipmentId, UUID orderId, ProductItem[] productItems, OffsetDateTime now) {
    if (!Arrays.stream(productItems).allMatch(isProductAvailable::apply)) {
      enqueue(new ProductWasOutOfStock(shipmentId, orderId, productItems, now));
      return;
    }

    enqueue(new PackageWasSent(shipmentId, orderId, productItems, now));
  }

  @Override
  public void when(ShipmentEvent event) {
    switch (event) {
      case PackageWasSent packageWasSent -> {
        id = packageWasSent.shipmentId();
        productItems = packageWasSent.productItems();
        status = Status.Sent;
      }
      case ProductWasOutOfStock outOfStock -> {
        id = outOfStock.shipmentId();
        productItems = outOfStock.productItems();
        status = Status.ProductsOutOfStock;
      }
    }
  }
}
