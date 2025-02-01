package io.eventdriven.introductiontoeventsourcing.e03_appending_event.mongodb;

import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.testing.tools.mongodb.MongoDBTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e03_appending_event.mongodb.AppendingEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AppendingEventsTests extends MongoDBTest {
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

  private EventStore.AppendResult appendEvents(MongoDBEventStore eventStore, String streamName, Object[] events) {
    // 1. Add logic here
    throw new RuntimeException("Not implemented!");
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

    var eventStore = getMongoEventStoreWith(storage);

    var streamName = "shopping_cart-%s".formatted(shoppingCartId);

    var nextExpectedStreamPosition = assertDoesNotThrow(() -> {
      var result = appendEvents(eventStore, streamName, events);
      return result.nextExpectedStreamPosition();
    });
    assertEquals(nextExpectedStreamPosition, events.length);
  }
}
