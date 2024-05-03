package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable;

import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.productItems.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.tools.EventStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.FunctionalTools.FoldLeft.foldLeft;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.productItems.ProductItems.ProductItem;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.ShoppingCartEvent.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.ShoppingCartService.ShoppingCartCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.ShoppingCartService.handle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BusinessLogicTests {
  static ShoppingCart getShoppingCart(EventStore eventStore, UUID shoppingCartId) {
    var events = eventStore.readStream(ShoppingCartEvent.class, shoppingCartId);

    return events.stream()
      .collect(foldLeft(ShoppingCart::initial, ShoppingCart::evolve));
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
    ShoppingCartEvent result = handle(new OpenShoppingCart(shoppingCartId, clientId));
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Add Two Pair of Shoes
    var shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = handle(
      FakeProductPriceCalculator.returning(shoesPrice),
      new AddProductItemToShoppingCart(shoppingCartId, twoPairsOfShoes),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Add T-Shirt
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = handle(
      FakeProductPriceCalculator.returning(tShirtPrice),
      new AddProductItemToShoppingCart(shoppingCartId, tShirt),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Remove a pair of shoes
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = handle(
      new RemoveProductItemFromShoppingCart(shoppingCartId, pricedPairOfShoes),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Confirm
    shoppingCart = getShoppingCart(eventStore, shoppingCartId);
    result = handle(
      new ConfirmShoppingCart(shoppingCartId),
      shoppingCart
    );
    eventStore.appendToStream(shoppingCartId, new Object[]{result});

    // Try Cancel
    ShoppingCart finalShoppingCart = getShoppingCart(eventStore, shoppingCartId);
    assertThrows(IllegalStateException.class, () -> {
      var cancelResult = handle(
        new CancelShoppingCart(shoppingCartId),
        finalShoppingCart
      );
      eventStore.appendToStream(shoppingCartId, new Object[]{cancelResult});
    });


    shoppingCart = getShoppingCart(eventStore, shoppingCartId);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().size());
    assertEquals(ShoppingCart.Status.Confirmed, shoppingCart.status());

    assertEquals(shoesId, shoppingCart.productItems().get(0).productId());
    assertEquals(pairOfShoes.quantity(), shoppingCart.productItems().get(0).quantity());
    assertEquals(pricedPairOfShoes.unitPrice(), shoppingCart.productItems().get(0).unitPrice());

    assertEquals(tShirtId, shoppingCart.productItems().get(1).productId());
    assertEquals(tShirt.quantity(), shoppingCart.productItems().get(1).quantity());
    assertEquals(pricedTShirt.unitPrice(), shoppingCart.productItems().get(1).unitPrice());

    assertThat(shoppingCart.productItems().get(0)).usingRecursiveComparison().isEqualTo(pricedPairOfShoes);
    assertThat(shoppingCart.productItems().get(1)).usingRecursiveComparison().isEqualTo(pricedTShirt);

    var events = eventStore.readStream(ShoppingCartEvent.class, shoppingCartId);
    assertThat(events).hasSize(5);
    assertThat(events.get(0)).isInstanceOf(ShoppingCartOpened.class);
    assertThat(events.get(1)).isInstanceOf(ProductItemAddedToShoppingCart.class);
    assertThat(events.get(2)).isInstanceOf(ProductItemAddedToShoppingCart.class);
    assertThat(events.get(3)).isInstanceOf(ProductItemRemovedFromShoppingCart.class);
    assertThat(events.get(4)).isInstanceOf(ShoppingCartConfirmed.class);
  }
}
