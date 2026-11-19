package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.ShoppingCartEvent.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.ProductItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.tools.EventStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class BusinessLogicTests {
    static ShoppingCart getShoppingCart(EventStore eventStore, UUID shoppingCartId) {
        // 1. Add logic here
        var shoppingCart = ShoppingCart.initial();

        for (var event : eventStore.readStream(ShoppingCartEvent.class, shoppingCartId)) {
            shoppingCart.evolve(event);
        }

        return shoppingCart;
    }

    @Test
    public void runningSequenceOfBusinessLogic_ShouldGenerateSequenceOfEvents() {
        var shoppingCartId = UUID.randomUUID();
        var clientId = UUID.randomUUID();
        var shoesId = UUID.randomUUID();
        var tShirtId = UUID.randomUUID();
        var twoPairsOfShoes = new ProductItem(shoesId, 2);
        var pairOfShoes = new ProductItem(shoesId, 1);
        var tShirt = new ProductItem(tShirtId, 1);

        var shoesPrice = 100;
        var tShirtPrice = 50;

        var pricedPairOfShoes = new PricedProductItem(shoesId, 1, shoesPrice);
        var pricedTShirt = new PricedProductItem(tShirtId, 1, tShirtPrice);

        var eventStore = new EventStore();

        // Open
        var opened = ShoppingCart.open(shoppingCartId, clientId);
        eventStore.appendToStream(shoppingCartId, new Object[] {opened.getKey()});

        // Add Two Pair of Shoes
        var shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        ShoppingCartEvent result =
                shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), twoPairsOfShoes);
        eventStore.appendToStream(shoppingCartId, new Object[] {result});

        // Add T-Shirt
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        result = shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), tShirt);
        eventStore.appendToStream(shoppingCartId, new Object[] {result});

        // Remove a pair of shoes
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        result = shoppingCart.removeProduct(pricedPairOfShoes);
        eventStore.appendToStream(shoppingCartId, new Object[] {result});

        // Confirm
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        result = shoppingCart.confirm();
        eventStore.appendToStream(shoppingCartId, new Object[] {result});

        // Try Cancel
        ShoppingCart finalShoppingCart = getShoppingCart(eventStore, shoppingCartId);
        assertThatThrownBy(() -> {
                    var cancelResult = finalShoppingCart.cancel();
                    eventStore.appendToStream(shoppingCartId, new Object[] {cancelResult});
                })
                .isInstanceOf(IllegalStateException.class);

        shoppingCart = getShoppingCart(eventStore, shoppingCartId);

        assertThat(shoppingCart.id()).isEqualTo(shoppingCartId);
        assertThat(shoppingCart.clientId()).isEqualTo(clientId);
        assertThat(shoppingCart.productItems().length).isEqualTo(2);
        assertThat(shoppingCart.status()).isEqualTo(ShoppingCart.Status.Confirmed);

        assertThat(shoppingCart.productItems()[0].productId()).isEqualTo(shoesId);
        assertThat(shoppingCart.productItems()[0].quantity()).isEqualTo(pairOfShoes.quantity());
        assertThat(shoppingCart.productItems()[0].unitPrice()).isEqualTo(pricedPairOfShoes.unitPrice());

        assertThat(shoppingCart.productItems()[1].productId()).isEqualTo(tShirtId);
        assertThat(shoppingCart.productItems()[1].quantity()).isEqualTo(tShirt.quantity());
        assertThat(shoppingCart.productItems()[1].unitPrice()).isEqualTo(pricedTShirt.unitPrice());

        assertThat(shoppingCart.productItems()[0]).usingRecursiveComparison().isEqualTo(pricedPairOfShoes);
        assertThat(shoppingCart.productItems()[1]).usingRecursiveComparison().isEqualTo(pricedTShirt);

        var events = eventStore.readStream(ShoppingCartEvent.class, shoppingCartId);
        assertThat(events).hasSize(5);
        assertThat(events.get(0)).isInstanceOf(ShoppingCartOpened.class);
        assertThat(events.get(1)).isInstanceOf(ProductItemAddedToShoppingCart.class);
        assertThat(events.get(2)).isInstanceOf(ProductItemAddedToShoppingCart.class);
        assertThat(events.get(3)).isInstanceOf(ProductItemRemovedFromShoppingCart.class);
        assertThat(events.get(4)).isInstanceOf(ShoppingCartConfirmed.class);
    }
}
