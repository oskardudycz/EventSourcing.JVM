package io.eventdriven.ecommerce.shoppingcarts;

import java.util.EnumSet;

public enum ShoppingCartStatus {
  Pending,
  Confirmed,
  Canceled;

  public static final EnumSet<ShoppingCartStatus> Closed = EnumSet.of(Confirmed, Canceled);

  public boolean isClosed() {
    return Closed.contains(this);
  }
}
