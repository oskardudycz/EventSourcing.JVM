package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution2;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution2.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution2.BusinessLogic.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution2.BusinessLogic.ShoppingCartStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BusinessLogicTests {
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

  static ShoppingCart getShoppingCart(Object[] events) {
    // 1. Add logic here
    var shoppingCartEvents = Arrays.stream(events)
      .filter(ShoppingCartEvent.class::isInstance)
      .map(ShoppingCartEvent.class::cast)
      .toList();

    var shoppingCart = new ShoppingCart();

    for (var event : shoppingCartEvents) {
      shoppingCart.when(event);
    }

    return shoppingCart;
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

    var events = new ArrayList<>();

    // Open
    var result = ShoppingCart.open(shoppingCartId, clientId);
    var shoppingCart = result.getValue();
    events.add(result.getKey());

    // Add two pairs of shoes
    shoppingCart = getShoppingCart(events.toArray());
    events.add(
      shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), twoPairsOfShoes)
    );

    // Add T-Shirt
    shoppingCart = getShoppingCart(events.toArray());
    events.add(
      shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), tShirt)
    );

    // Remove pair of shoes
    // Hack alert!
    //
    // See that's why immutability is so cool, as it's predictable
    // As we're sharing objects (e.g. in PricedProductItem in events)
    // then adding them into list and changing it while appending/removing
    // then we can have unpleasant surprises.
    //
    // This will not likely happen if all objects are recreated (e.g. in the web requests)
    // However when it happens then it's tricky to diagnose.
    // Uncomment lines below and debug to find more.
    // shoppingCart = getShoppingCart(events.toArray());
    events.add(
      shoppingCart.removeProduct(pricedPairOfShoes)
    );

    // Confirm
    // Uncomment line below and debug to find bug.
    // shoppingCart = getShoppingCart(events.toArray());
    events.add(
      shoppingCart.confirm()
    );

    // Cancel
    ShoppingCart finalShoppingCart = shoppingCart;
    assertThrows(IllegalStateException.class, () ->
      events.add(
        finalShoppingCart.cancel()
      )
    );

    // Uncomment line below and debug to find bug.
    // shoppingCart = getShoppingCart(events.toArray());

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.status());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(pricedPairOfShoes, shoppingCart.productItems()[0]);
    assertEquals(pricedTShirt, shoppingCart.productItems()[1]);
  }
}
