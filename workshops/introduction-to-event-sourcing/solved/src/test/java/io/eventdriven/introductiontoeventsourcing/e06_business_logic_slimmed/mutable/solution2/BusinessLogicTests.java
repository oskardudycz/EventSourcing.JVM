package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2;

import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.tools.EventStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.ProductItem;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution2.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    eventStore.appendToStream(shoppingCartId, new Object[]{opened.getKey()});

    // Add Two Pair of Shoes
    var shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    ShoppingCartEvent result = shoppingCart.addProduct(
      FakeProductPriceCalculator.returning(shoesPrice),
      twoPairsOfShoes
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Add T-Shirt
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = shoppingCart.addProduct(
      FakeProductPriceCalculator.returning(tShirtPrice),
      tShirt
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Remove a pair of shoes
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = shoppingCart.removeProduct(pricedPairOfShoes);
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Confirm
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = shoppingCart.confirm();
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Try Cancel
    ShoppingCart finalShoppingCart = getShoppingCart(eventStore, shoppingCartId);
    assertThrows(IllegalStateException.class, () -> {
      var cancelResult = finalShoppingCart.cancel();
      eventStore.appendToStream(shoppingCartId, new Object[]{cancelResult});
    });


    shoppingCart = getShoppingCart(eventStore, shoppingCartId);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().length);
    assertEquals(ShoppingCart.Status.Confirmed, shoppingCart.status());

    assertEquals(shoesId, shoppingCart.productItems()[0].productId());
    assertEquals(pairOfShoes.quantity(), shoppingCart.productItems()[0].quantity());
    assertEquals(pricedPairOfShoes.unitPrice(), shoppingCart.productItems()[0].unitPrice());

    assertEquals(tShirtId, shoppingCart.productItems()[1].productId());
    assertEquals(tShirt.quantity(), shoppingCart.productItems()[1].quantity());
    assertEquals(pricedTShirt.unitPrice(), shoppingCart.productItems()[1].unitPrice());

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
