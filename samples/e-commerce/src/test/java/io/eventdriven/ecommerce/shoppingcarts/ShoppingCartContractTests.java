package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsController.AddProductItemRequest;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.testing.contracts.Contracts;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ShoppingCartContractTests {
    private static final UUID customerId = uuid(1);
    private static final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);
    private static final PricedProductItem productItem = new PricedProductItem(new ProductItem(uuid(2), 2), 64.995);

    @Nested
    class StoredEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new ProductItemAddedToShoppingCart(cartId, customerId, productItem, at),
                    new ProductItemRemovedFromShoppingCart(cartId, productItem, at),
                    new ProductItemPriceChanged(cartId, uuid(2), 64.995, 59.99, at),
                    new ShoppingCartEvent.ShoppingCartCheckedOut(cartId, at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }

    @Nested
    class PublishedEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                    cartId, customerId, new PricedProductItem[] {productItem}, 129.99, at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }

    @Nested
    class RequestBody {
        @Test
        void ofAddingAProductKeepsItsFormat() {
            MessageContract.specification(options)
                    .given(new AddProductItemRequest(uuid(2), 2))
                    .whenSerialized()
                    .thenContractIsUnchanged();
        }
    }
}
