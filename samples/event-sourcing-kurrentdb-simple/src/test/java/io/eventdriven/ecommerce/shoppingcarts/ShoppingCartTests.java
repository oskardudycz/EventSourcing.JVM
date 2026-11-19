package io.eventdriven.ecommerce.shoppingcarts;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart.*;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartConfirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartTests {
    @Test
    void aggregationWorks() {
        var clientId = UUID.randomUUID();
        var shoppingCartId = UUID.randomUUID();

        var shoes = new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 100);

        var tShirt = new PricedProductItem(new ProductItem(UUID.randomUUID(), 2), 40);

        var events = new ShoppingCartEvent[] {
            new ShoppingCartOpened(shoppingCartId, clientId),
            new ProductItemAddedToShoppingCart(shoppingCartId, shoes),
            new ProductItemAddedToShoppingCart(shoppingCartId, tShirt),
            new ProductItemRemovedFromShoppingCart(shoppingCartId, shoes),
            new ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now())
        };

        var shoppingCart = ShoppingCart.empty();
        for (var event : events) {
            shoppingCart = ShoppingCart.when(shoppingCart, event);
        }

        assertThat(shoppingCart instanceof Confirmed).isTrue();
        var confirmedShoppingCart = (Confirmed) shoppingCart;
        assertThat(shoppingCartId).isEqualTo(confirmedShoppingCart.id());
        assertThat(clientId).isEqualTo(confirmedShoppingCart.clientId());
        assertThat(confirmedShoppingCart.isClosed()).isTrue();

        assertThat(confirmedShoppingCart.productItems().items().stream().count())
                .isEqualTo(1);

        var tShirtFromShoppingCart =
                confirmedShoppingCart.productItems().items().get(0);
        assertThat(tShirtFromShoppingCart).isEqualTo(tShirt);
    }
}
