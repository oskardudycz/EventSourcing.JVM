package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.mongodb.immutable.solution1;

import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.testing.tools.mongodb.MongoDBTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.mongodb.immutable.FunctionalTools.groupingByOrdered;
import static io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.mongodb.immutable.solution1.GettingStateFromEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GettingStateFromEventsTests extends MongoDBTest {
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

  static EventStore.AppendResult appendEvents(MongoDBEventStore eventStore, StreamName streamName, Object[] events) {
    // 1. Add logic here
    return eventStore.appendToStream(streamName, Arrays.stream(events).toList());
  }

  static ShoppingCart getShoppingCart(MongoDBEventStore eventStore, StreamName streamName) {
    // 1. Add logic here
    return eventStore.<ShoppingCart, ShoppingCartEvent>aggregateStream(
      () -> null,
      (shoppingCart, event) -> {
        switch (event) {
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
                .map(pi -> pi.productId().equals(productItemRemoved.productItem().productId()) ?
                  new PricedProductItem(
                    pi.productId(),
                    pi.quantity() - productItemRemoved.productItem().quantity(),
                    pi.unitPrice()
                  )
                  : pi
                )
                .filter(pi -> pi.quantity > 0)
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
        return shoppingCart;
      },
      streamName
    ).state();
  }

  @ParameterizedTest
  @MethodSource("mongoEventStorages")
  public void appendingEvents_forSequenceOfEvents_shouldSucceed(MongoDBEventStore.Storage storage) {
    var shoppingCartId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var shoesId = UUID.randomUUID();
    var tShirtId = UUID.randomUUID();
    var twoPairsOfShoes = new PricedProductItem(shoesId, 2, 100);
    var pairOfShoes = new PricedProductItem(shoesId, 1, 100);
    var tShirt = new PricedProductItem(tShirtId, 1, 50);

    var events = new ShoppingCartEvent[]
      {
        new ShoppingCartOpened(shoppingCartId, clientId),
        new ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes),
        new ProductItemAddedToShoppingCart(shoppingCartId, tShirt),
        new ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes),
        new ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()),
        new ShoppingCartCanceled(shoppingCartId, OffsetDateTime.now())
      };

    var streamName = new StreamName("shopping_cart", shoppingCartId.toString());

    var eventStore = getMongoEventStoreWith(storage);

    appendEvents(eventStore, streamName, events);

    var shoppingCart = getShoppingCart(eventStore, streamName);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(pairOfShoes, shoppingCart.productItems()[0]);
    assertEquals(tShirt, shoppingCart.productItems()[1]);
  }
}
