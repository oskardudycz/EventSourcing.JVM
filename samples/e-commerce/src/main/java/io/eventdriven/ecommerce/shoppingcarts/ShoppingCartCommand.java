package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public sealed interface ShoppingCartCommand {
    record AddProductItemToShoppingCart(
            ShoppingCart.Id shoppingCartId,
            UUID customerId,
            ProductItem productItem,
            @Nullable Double currentUnitPrice,
            @Nullable OffsetDateTime now)
            implements ShoppingCartCommand {
        public AddProductItemToShoppingCart(ShoppingCart.Id shoppingCartId, UUID customerId, ProductItem productItem) {
            this(shoppingCartId, customerId, productItem, null, null);
        }
    }

    record RemoveProductItemFromShoppingCart(
            ShoppingCart.Id shoppingCartId,
            ProductItem productItem,
            Long expectedVersion,
            @Nullable OffsetDateTime now) implements ShoppingCartCommand {
        public RemoveProductItemFromShoppingCart(
                ShoppingCart.Id shoppingCartId, ProductItem productItem, Long expectedVersion) {
            this(shoppingCartId, productItem, expectedVersion, null);
        }
    }

    record CheckOutShoppingCart(
            ShoppingCart.Id shoppingCartId,
            Long expectedVersion,
            @Nullable Map<UUID, Double> currentUnitPrices,
            @Nullable OffsetDateTime now)
            implements ShoppingCartCommand {
        public CheckOutShoppingCart(ShoppingCart.Id shoppingCartId, Long expectedVersion) {
            this(shoppingCartId, expectedVersion, null, null);
        }
    }
}
