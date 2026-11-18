package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartExternalEvent {
    @Schema(
            description =
                    "The customer checked out the shopping cart. It carries the customer, the products, their prices and the total.")
    record ShoppingCartCheckedOut(
            ShoppingCartId cartId,
            UUID customerId,
            PricedProductItem[] productItems,
            double totalPrice,
            OffsetDateTime checkedOutAt)
            implements ShoppingCartExternalEvent {}
}
