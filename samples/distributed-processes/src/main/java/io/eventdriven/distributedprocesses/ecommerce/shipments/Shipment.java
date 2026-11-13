package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.function.Function;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

public class Shipment extends AbstractAggregate<ShipmentEvent, ShipmentId> {
  enum Status {
    Reserved,
    FulfillmentRequested,
    FulfillmentRejected,
    Sent,
    Delivered,
    Released,
    ProductsOutOfStock
  }

  private String referenceId;
  private ProductItem[] productItems;
  private Status status;

  private Shipment() {
  }

  public static Shipment empty() {
    return new Shipment();
  }

  public void reserveStock(
    Function<ProductItem, Boolean> isProductAvailable,
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime now
  ) {
    if (status != null)
      return;

    // allMatch on an empty stream is true
    if (productItems.length == 0
      || !Arrays.stream(productItems).allMatch(isProductAvailable::apply)) {
      enqueue(new ProductWasOutOfStock(shipmentId, referenceId, productItems, now));
      return;
    }

    enqueue(new StockReserved(shipmentId, referenceId, productItems, now));
  }

  public void requestFulfillment(OffsetDateTime now) {
    if (status != Status.Reserved)
      return;

    enqueue(new FulfillmentRequested(id, referenceId, productItems, now));
  }

  public void confirmSent(String carrier, String trackingNumber, OffsetDateTime now) {
    if (status != Status.FulfillmentRequested)
      return;

    enqueue(new PackageWasSent(id, referenceId, carrier, trackingNumber, now));
  }

  public void rejectFulfillment(OffsetDateTime now) {
    if (status != Status.FulfillmentRequested)
      return;

    enqueue(new FulfillmentRejected(id, referenceId, now));
  }

  public void deliver(OffsetDateTime now) {
    if (status != Status.Sent)
      return;

    enqueue(new PackageWasDelivered(id, referenceId, now));
  }

  public void releaseStock(OffsetDateTime now) {
    if (!isStockHeld())
      return;

    enqueue(new StockReleased(id, referenceId, now));
  }

  private boolean isStockHeld() {
    return status == Status.Reserved
      || status == Status.FulfillmentRequested
      || status == Status.FulfillmentRejected;
  }

  @Override
  public void evolve(ShipmentEvent event) {
    switch (event) {
      case StockReserved stockReserved -> {
        id = stockReserved.shipmentId();
        referenceId = stockReserved.referenceId();
        productItems = stockReserved.productItems();
        status = Status.Reserved;
      }
      case ProductWasOutOfStock outOfStock -> {
        id = outOfStock.shipmentId();
        referenceId = outOfStock.referenceId();
        status = Status.ProductsOutOfStock;
      }
      case FulfillmentRequested fulfillmentRequested -> status = Status.FulfillmentRequested;
      case FulfillmentRejected fulfillmentRejected -> status = Status.FulfillmentRejected;
      case PackageWasSent packageWasSent -> status = Status.Sent;
      case PackageWasDelivered packageWasDelivered -> status = Status.Delivered;
      case StockReleased stockReleased -> status = Status.Released;
    }
  }
}
