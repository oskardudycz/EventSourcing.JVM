package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public class ShoppingCartSummary implements Versioned {
  private ShoppingCartId cartId;
  private UUID clientId;
  private PricedProductItem[] productItems;
  private double totalPrice;
  private ShoppingCartStatus status;
  private long version;

  public ShoppingCartSummary() {
  }

  public ShoppingCartSummary(
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    ShoppingCartStatus status
  ) {
    this.cartId = cartId;
    this.clientId = clientId;
    this.productItems = productItems;
    this.totalPrice = totalPrice;
    this.status = status;
  }

  public ShoppingCartId getCartId() {
    return cartId;
  }

  public void setCartId(ShoppingCartId cartId) {
    this.cartId = cartId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public void setClientId(UUID clientId) {
    this.clientId = clientId;
  }

  public PricedProductItem[] getProductItems() {
    return productItems;
  }

  public void setProductItems(PricedProductItem[] productItems) {
    this.productItems = productItems;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(double totalPrice) {
    this.totalPrice = totalPrice;
  }

  public ShoppingCartStatus getStatus() {
    return status;
  }

  public void setStatus(ShoppingCartStatus status) {
    this.status = status;
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
