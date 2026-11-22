package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartEvent {

    record ProductItemAddedToShoppingCart(
            ShoppingCart.Id shoppingCartId, UUID customerId, PricedProductItem productItem, OffsetDateTime addedAt)
            implements ShoppingCartEvent {}

    record ProductItemRemovedFromShoppingCart(
            ShoppingCart.Id shoppingCartId, PricedProductItem productItem, OffsetDateTime removedAt)
            implements ShoppingCartEvent {}

    record ProductItemPriceChanged(
            ShoppingCart.Id shoppingCartId,
            UUID productId,
            double oldUnitPrice,
            double newUnitPrice,
            OffsetDateTime changedAt)
            implements ShoppingCartEvent {}

    record ShoppingCartCheckedOut(ShoppingCart.Id shoppingCartId, OffsetDateTime checkedOutAt)
            implements ShoppingCartEvent {}
}
