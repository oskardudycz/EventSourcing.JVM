package io.eventdriven.introductiontoeventsourcing.e03_appending_event.esdb;

import com.eventstore.dbclient.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.eventdriven.introductiontoeventsourcing.e03_appending_event.esdb.AppendingEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.*;

public class AppendingEventsTests {
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

  private ESDBSerializer serializer = new ESDBSerializer();

  private CompletableFuture<WriteResult> appendEvents(EventStoreDBClient eventStore, String streamName, Object[] events)
  {
    // 1. Add logic here
    throw new RuntimeException("Not implemented!");
  }

  @Tag("Exercise")
  @Test
  public void AppendingEvents_ForSequenceOfEvents_ShouldSucceed() {
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

    var settings = EventStoreDBConnectionString.parseOrThrow("esdb://localhost:2113?tls=false");

    var eventStore = EventStoreDBClient.create(settings);

    var streamName = "shopping_cart-%s".formatted(shoppingCartId);

    var nextStreamRevision = assertDoesNotThrow(() -> {
      var result = appendEvents(eventStore, streamName, events).get();
      return result.getNextExpectedRevision();
    });
    assertEquals(nextStreamRevision, ExpectedRevision.expectedRevision(events.length - 1));
  }
}
