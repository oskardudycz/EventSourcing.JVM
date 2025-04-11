package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable;

import io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.productItems.FakeProductPriceCalculator;
import io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.tools.EventStore;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.FunctionalTools.FoldLeft.foldLeft;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.productItems.ProductItems.ProductItem;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCart.Event.*;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCartDecider.Command.*;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.immutable.ShoppingCartDecider.decide;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class BusinessLogicTests {
  static ShoppingCart getShoppingCart(EventStore eventStore, UUID shoppingCartId) {
    var events = eventStore.readStream(ShoppingCart.Event.class, shoppingCartId);

    return events.stream()
      .collect(foldLeft(ShoppingCart.Initial::new, ShoppingCart::evolve));
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

    var pricedTwoPairOfShoes = new PricedProductItem(shoesId, 2, shoesPrice);
    var pricedPairOfShoes = new PricedProductItem(shoesId, 1, shoesPrice);
    var pricedTShirt = new PricedProductItem(tShirtId, 1, tShirtPrice);

    var eventStore = new EventStore();
    var now = OffsetDateTime.now();

    // Open
    var shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    ShoppingCart.Event result = decide(new Open(shoppingCartId, clientId, now), shoppingCart);
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Add Two Pair of Shoes
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = decide(
      new AddProductItem(shoppingCartId,
        FakeProductPriceCalculator.returning(shoesPrice).calculate(twoPairsOfShoes), now),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Add T-Shirt
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = decide(
      new AddProductItem(shoppingCartId,
        FakeProductPriceCalculator.returning(tShirtPrice).calculate(tShirt), now),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Remove a pair of shoes
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = decide(
      new RemoveProductItem(shoppingCartId, pricedPairOfShoes, now),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Confirm
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = decide(
      new Confirm(shoppingCartId, now),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Try Cancel
    ShoppingCart finalShoppingCart = getShoppingCart(eventStore, shoppingCartId);
    assertThrows(IllegalStateException.class, () -> {
      var cancelResult = decide(
        new Cancel(shoppingCartId, now),
        finalShoppingCart
      );
      eventStore.appendToStream(shoppingCartId, new Object[]{cancelResult});
    });


    shoppingCart = getShoppingCart(eventStore, shoppingCartId);

    assertThat(shoppingCart).isInstanceOf(ShoppingCart.Closed.class);

    var events = eventStore.readStream(ShoppingCart.Event.class, shoppingCartId);
    assertThat(events).hasSize(5);
    assertThat(events.get(0)).isEqualTo(new Opened(shoppingCartId, clientId, now));
    assertThat(events.get(1)).isEqualTo(new ProductItemAdded(shoppingCartId, pricedTwoPairOfShoes, now));
    assertThat(events.get(2)).isEqualTo(new ProductItemAdded(shoppingCartId, pricedTShirt, now));
    assertThat(events.get(3)).isEqualTo(new ProductItemRemoved(shoppingCartId, pricedPairOfShoes, now));
    assertThat(events.get(4)).isEqualTo(new Confirmed(shoppingCartId, now));
  }
}
