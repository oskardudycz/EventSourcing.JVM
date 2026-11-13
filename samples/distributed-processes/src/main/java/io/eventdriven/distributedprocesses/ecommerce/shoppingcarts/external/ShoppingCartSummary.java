package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public class ShoppingCartSummary implements Versioned {
  private UUID clientId;
  private PricedProductItem[] productItems;
  private double totalPrice;
  private long version;

  public ShoppingCartSummary() {
  }

  public ShoppingCartSummary(UUID clientId, PricedProductItem[] productItems, double totalPrice) {
    this.clientId = clientId;
    this.productItems = productItems;
    this.totalPrice = totalPrice;
  }

  public UUID getClientId() {
    return clientId;
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
