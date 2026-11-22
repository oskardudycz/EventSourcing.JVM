package io.eventdriven.ecommerce.shoppingcarts.external;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartExternalEvent {
    @Schema(
            description =
                    "The customer checked out the shopping cart. It carries the customer, the products, their prices and the total.")
    record ShoppingCartCheckedOut(
            ShoppingCart.Id cartId,
            UUID customerId,
            PricedProductItem[] productItems,
            double totalPrice,
            OffsetDateTime checkedOutAt)
            implements ShoppingCartExternalEvent {}
}
