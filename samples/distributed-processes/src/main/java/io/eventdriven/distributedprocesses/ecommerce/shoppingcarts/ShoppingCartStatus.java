package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import java.util.EnumSet;

public enum ShoppingCartStatus {
  Pending,
  CheckedOut;

  public static final EnumSet<ShoppingCartStatus> Closed = EnumSet.of(CheckedOut);

  public boolean isClosed() {
    return Closed.contains(this);
  }
}
