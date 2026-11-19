package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1.ProductItems.ProductItems.*;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1.ProductItems.*;
import io.eventdriven.introductiontoeventsourcing.e05_business_logic.tools.EventStore;
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
        var shoppingCart = ShoppingCart.open(shoppingCartId, clientId);
        eventStore.appendToStream(shoppingCartId, shoppingCart.dequeueUncommittedEvents());

        // Add Two Pair of Shoes
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), twoPairsOfShoes);
        eventStore.appendToStream(shoppingCartId, shoppingCart.dequeueUncommittedEvents());

        // Add T-Shirt
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), tShirt);
        eventStore.appendToStream(shoppingCartId, shoppingCart.dequeueUncommittedEvents());

        // Remove a pair of shoes
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        shoppingCart.removeProduct(pricedPairOfShoes);
        eventStore.appendToStream(shoppingCartId, shoppingCart.dequeueUncommittedEvents());

        // Confirm
        shoppingCart = getShoppingCart(eventStore, shoppingCartId);
        shoppingCart.confirm();
        eventStore.appendToStream(shoppingCartId, shoppingCart.dequeueUncommittedEvents());

        // Try Cancel
        ShoppingCart finalShoppingCart = getShoppingCart(eventStore, shoppingCartId);
        assertThatThrownBy(() -> {
                    finalShoppingCart.cancel();
                    eventStore.appendToStream(shoppingCartId, finalShoppingCart.dequeueUncommittedEvents());
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
