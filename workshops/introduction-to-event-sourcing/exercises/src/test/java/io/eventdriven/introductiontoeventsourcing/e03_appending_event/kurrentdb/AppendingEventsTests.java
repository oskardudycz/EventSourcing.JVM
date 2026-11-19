package io.eventdriven.introductiontoeventsourcing.e03_appending_event.kurrentdb;

import static io.eventdriven.introductiontoeventsourcing.e03_appending_event.kurrentdb.AppendingEventsTests.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventstores.testing.tools.TestDatabases;
import io.kurrent.dbclient.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class AppendingEventsTests {
    public sealed interface ShoppingCartEvent {
        record ShoppingCartOpened(UUID shoppingCartId, UUID clientId) implements ShoppingCartEvent {}

        record ProductItemAddedToShoppingCart(UUID shoppingCartId, PricedProductItem productItem)
                implements ShoppingCartEvent {}

        record ProductItemRemovedFromShoppingCart(UUID shoppingCartId, PricedProductItem productItem)
                implements ShoppingCartEvent {}

        record ShoppingCartConfirmed(UUID shoppingCartId, OffsetDateTime confirmedAt) implements ShoppingCartEvent {}

        record ShoppingCartCanceled(UUID shoppingCartId, OffsetDateTime canceledAt) implements ShoppingCartEvent {}
    }

    public record PricedProductItem(UUID productId, int quantity, double unitPrice) {
        public double totalAmount() {
            return quantity * unitPrice;
        }
    }

    private KurrentDBSerializer serializer = new KurrentDBSerializer();

    private CompletableFuture<WriteResult> appendEvents(
            KurrentDBClient eventStore, String streamName, Object[] events) {
        // 1. Add logic here
        throw new RuntimeException("Not implemented!");
    }

    @Tag("Exercise")
    @Test
    public void AppendingEvents_ForSequenceOfEvents_ShouldSucceed() throws Exception {
        var shoppingCartId = UUID.randomUUID();
        var clientId = UUID.randomUUID();
        var shoesId = UUID.randomUUID();
        var tShirtId = UUID.randomUUID();
        var twoPairsOfShoes = new PricedProductItem(shoesId, 2, 100);
        var pairOfShoes = new PricedProductItem(shoesId, 1, 100);
        var tShirt = new PricedProductItem(tShirtId, 1, 50);

        var events = new ShoppingCartEvent[] {
            new ShoppingCartOpened(shoppingCartId, clientId),
            new ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes),
            new ProductItemAddedToShoppingCart(shoppingCartId, tShirt),
            new ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes),
            new ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()),
            new ShoppingCartCanceled(shoppingCartId, OffsetDateTime.now())
        };

        var settings = KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());

        var eventStore = KurrentDBClient.create(settings);

        var streamName = "shopping_cart-%s".formatted(shoppingCartId);

        var result = appendEvents(eventStore, streamName, events).get();
        var nextStreamRevision = result.getNextExpectedRevision();
        assertThat(nextStreamRevision).isEqualTo(StreamState.streamRevision(events.length - 1));
    }
}
