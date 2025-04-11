package io.eventdriven.eventdrivenarchitecture.e03_appending_event.postgresql;

import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.postgresql.PostgreSQLEventStore;
import io.eventdriven.eventstores.testing.tools.postgresql.PostgreSQLTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_appending_event.postgresql.AppendingEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AppendingEventsTests extends PostgreSQLTest {
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

  private EventStore.AppendResult appendEvents(PostgreSQLEventStore eventStore, StreamName streamName, Object[] events) {
    // 1. Add logic here
    return eventStore.appendToStream(streamName, Arrays.stream(events).toList());
  }

  @Test
  public void appendingEvents_forSequenceOfEvents_shouldSucceed() {
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

    var eventStore = getPostgreSQLEventStore();

    var streamName = new StreamName("shopping_cart", shoppingCartId.toString());

    var nextExpectedStreamPosition = assertDoesNotThrow(() -> {
      var result = appendEvents(eventStore, streamName, events);
      return result.nextExpectedStreamPosition();
    });
    assertEquals(nextExpectedStreamPosition, events.length);
  }
}
