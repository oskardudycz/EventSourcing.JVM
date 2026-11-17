package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public class ShoppingCartSummary implements Versioned {
  private UUID customerId;
  private PricedProductItem[] productItems;
  private double totalPrice;
  private long version;

  public ShoppingCartSummary() {
  }

  public ShoppingCartSummary(UUID customerId, PricedProductItem[] productItems, double totalPrice) {
    this.customerId = customerId;
    this.productItems = productItems;
    this.totalPrice = totalPrice;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public PricedProductItem[] getProductItems() {
    return productItems;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public void setVersion(long version) {
    this.version = version;
  }
}
