package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartExternalEvent {
  record ShoppingCartFinalized(
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime finalizedAt
  ) implements ShoppingCartExternalEvent {
  }
}
