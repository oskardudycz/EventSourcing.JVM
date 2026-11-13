package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.util.UUID;

public class ClientShoppingCart implements Versioned {
  private UUID clientId;
  private ShoppingCartId shoppingCartId;
  private ShoppingCartStatus status;
  private long version;

  public ClientShoppingCart() {
  }

  public ClientShoppingCart(UUID clientId, ShoppingCartId shoppingCartId, ShoppingCartStatus status) {
    this.clientId = clientId;
    this.shoppingCartId = shoppingCartId;
    this.status = status;
  }

  public UUID getClientId() {
    return clientId;
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
