package io.eventdriven.introductiontoeventsourcing.e02_getting_state_from_events.immutable.solution1;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e02_getting_state_from_events.immutable.FunctionalTools.groupingByOrdered;
import static io.eventdriven.introductiontoeventsourcing.e02_getting_state_from_events.immutable.solution1.GettingStateFromEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GettingStateFromEventsTests {
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

  // ENTITY
  public record ShoppingCart(
    UUID id,
    UUID clientId,
    ShoppingCartStatus status,
    PricedProductItem[] productItems,
    OffsetDateTime confirmedAt,
    OffsetDateTime canceledAt) {
  }

  public enum ShoppingCartStatus {
    Pending,
    Confirmed,
    Canceled
  }

  static ShoppingCart getShoppingCart(Object[] events) {
    // 1. Add logic here
    ShoppingCart shoppingCart = null;

    for (var event : events) {
      if (!(event instanceof ShoppingCartEvent shoppingCartEvent))
        continue;

      switch (shoppingCartEvent) {
        case ShoppingCartOpened opened -> shoppingCart = new ShoppingCart(
          opened.shoppingCartId(),
          opened.clientId(),
          ShoppingCartStatus.Pending,
          new PricedProductItem[]{},
          null,
          null
        );
        case ProductItemAddedToShoppingCart productItemAdded ->
          shoppingCart = new ShoppingCart(
            shoppingCart.id(),
            shoppingCart.clientId(),
            shoppingCart.status(),
            Stream.concat(Arrays.stream(shoppingCart.productItems()), Stream.of(productItemAdded.productItem()))
              .collect(groupingByOrdered(PricedProductItem::productId))
              .entrySet().stream()
              .map(group -> group.getValue().size() == 1 ?
                group.getValue().get(0) :
                new PricedProductItem(
                  group.getKey(),
                  group.getValue().stream().mapToInt(PricedProductItem::quantity).sum(),
                  group.getValue().get(0).unitPrice()
                )
              )
              .toArray(PricedProductItem[]::new),
            shoppingCart.confirmedAt(),
            shoppingCart.canceledAt()
          );
        case ProductItemRemovedFromShoppingCart productItemRemoved ->
          shoppingCart = new ShoppingCart(
            shoppingCart.id(),
            shoppingCart.clientId(),
            shoppingCart.status(),
            Arrays.stream(shoppingCart.productItems())
              .map(pi -> pi.productId() == productItemRemoved.productItem().productId() ?
                new PricedProductItem(
                  pi.productId(),
                  pi.quantity() - productItemRemoved.productItem().quantity(),
                  pi.unitPrice()
                )
                : pi
              )
              .filter(pi -> pi.quantity() > 0)
              .toArray(PricedProductItem[]::new),
            shoppingCart.confirmedAt(),
            shoppingCart.canceledAt()
          );
        case ShoppingCartConfirmed confirmed -> shoppingCart = new ShoppingCart(
          shoppingCart.id(),
          shoppingCart.clientId(),
          ShoppingCartStatus.Confirmed,
          shoppingCart.productItems(),
          confirmed.confirmedAt(),
          shoppingCart.canceledAt()
        );
        case ShoppingCartCanceled canceled -> shoppingCart = new ShoppingCart(
          shoppingCart.id(),
          shoppingCart.clientId(),
          ShoppingCartStatus.Canceled,
          shoppingCart.productItems(),
          shoppingCart.confirmedAt(),
          canceled.canceledAt()
        );
      }
    }

    return shoppingCart;
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() {
    var shoppingCartId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var shoesId = UUID.randomUUID();
    var tShirtId = UUID.randomUUID();
    var twoPairsOfShoes = new PricedProductItem(shoesId, 2, 100);
    var pairOfShoes = new PricedProductItem(shoesId, 1, 100);
    var tShirt = new PricedProductItem(tShirtId, 1, 50);

    var events = new Object[]
      {
        new ShoppingCartEvent.ShoppingCartOpened(shoppingCartId, clientId),
        new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes),
        new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, tShirt),
        new ShoppingCartEvent.ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes),
        new ShoppingCartEvent.ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()),
        new ShoppingCartEvent.ShoppingCartCanceled(shoppingCartId, OffsetDateTime.now())
      };

    var shoppingCart = getShoppingCart(events);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(pairOfShoes, shoppingCart.productItems()[0]);
    assertEquals(tShirt, shoppingCart.productItems()[1]);
  }
}
