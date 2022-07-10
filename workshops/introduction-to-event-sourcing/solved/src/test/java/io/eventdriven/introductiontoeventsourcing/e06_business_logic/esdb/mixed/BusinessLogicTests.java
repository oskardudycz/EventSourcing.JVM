package io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed;

import io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogic.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogic.ShoppingCartCommand;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogic.ShoppingCartStatus;
import io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.mixed.BusinessLogic.ShoppingCartCommand.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BusinessLogicTests extends EventStoreDBTest {
  public sealed interface ShoppingCartEvent {
    record ShoppingCartOpened(
      UUID shoppingCartId,
      UUID clientId
    ) implements ShoppingCartEvent {
    }

    record ProductItemAddedToShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ProductItemRemovedFromShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartConfirmed(
      UUID shoppingCartId,
      OffsetDateTime confirmedAt
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartCanceled(
      UUID shoppingCartId,
      OffsetDateTime canceledAt
    ) implements ShoppingCartEvent {
    }
  }

  public record PricedProductItem(
    UUID productId,
    int quantity,
    double unitPrice
  ) {
    public double totalAmount() {
      return quantity * unitPrice;
    }
  }

  public record ProductItem(
    UUID productId,
    int quantity
  ) {
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() {
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

    var entityStore = new EntityStore<ShoppingCart, ShoppingCartEvent, ShoppingCartCommand>(
      ShoppingCart.class,
      ShoppingCartEvent.class,
      this.eventStore,
      ShoppingCart::empty,
      "shopping_cart-%s"::formatted
    );

    // Open
    var openShoppingCart = new OpenShoppingCart(shoppingCartId, clientId);
    entityStore.add(
      openShoppingCart.shoppingCartId(),
      ShoppingCart.open(openShoppingCart.shoppingCartId(), openShoppingCart.clientId()).getKey()
    );

    // Add two pairs of shoes
    var addTwoPairsOfShoes = new AddProductItemToShoppingCart(shoppingCartId, twoPairsOfShoes);
    entityStore.getAndUpdate(
      addTwoPairsOfShoes.shoppingCartId(),
      addTwoPairsOfShoes,
      (command, shoppingCart) ->
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), command.productItem())
    );

    // Add T-Shirt
    var addTShirt = new AddProductItemToShoppingCart(shoppingCartId, tShirt);
    entityStore.getAndUpdate(
      addTShirt.shoppingCartId(),
      addTShirt,
      (command, shoppingCart) ->
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), command.productItem())
    );

    // Remove pair of shoes
    var removePairOfShoes = new RemoveProductItemFromShoppingCart(shoppingCartId, pricedPairOfShoes);
    entityStore.getAndUpdate(
      removePairOfShoes.shoppingCartId(),
      removePairOfShoes,
      (command, shoppingCart) ->
        shoppingCart.removeProduct(command.productItem())
    );

    // Confirm
    var confirmShoppingCart = new ConfirmShoppingCart(shoppingCartId);
    entityStore.getAndUpdate(
      confirmShoppingCart.shoppingCartId(),
      confirmShoppingCart,
      (command, shoppingCart) ->
        shoppingCart.confirm()
    );

    // Cancel
    assertThrows(IllegalStateException.class, () -> {
      var cancelShoppingCart = new CancelShoppingCart(shoppingCartId);
      entityStore.getAndUpdate(
        cancelShoppingCart.shoppingCartId(),
        cancelShoppingCart,
        (command, shoppingCart) ->
          shoppingCart.cancel()
      );
    });

    var shoppingCart = entityStore.get(shoppingCartId);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.status());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(pricedPairOfShoes, shoppingCart.productItems()[0]);
    assertEquals(pricedTShirt, shoppingCart.productItems()[1]);
  }
}
