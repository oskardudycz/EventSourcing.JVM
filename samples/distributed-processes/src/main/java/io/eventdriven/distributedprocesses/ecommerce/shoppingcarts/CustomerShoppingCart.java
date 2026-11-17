package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.util.UUID;

public class CustomerShoppingCart implements Versioned {
  private UUID customerId;
  private ShoppingCartId shoppingCartId;
  private ShoppingCartStatus status;
  private long version;

  public CustomerShoppingCart() {
  }

  public CustomerShoppingCart(UUID customerId, ShoppingCartId shoppingCartId, ShoppingCartStatus status) {
    this.customerId = customerId;
    this.shoppingCartId = shoppingCartId;
    this.status = status;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public ShoppingCartId getShoppingCartId() {
    return shoppingCartId;
  }

  public ShoppingCartStatus getStatus() {
    return status;
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
