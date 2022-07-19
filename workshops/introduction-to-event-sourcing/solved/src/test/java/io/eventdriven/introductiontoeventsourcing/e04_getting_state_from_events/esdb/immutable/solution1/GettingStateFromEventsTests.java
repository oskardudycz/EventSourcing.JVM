package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.esdb.immutable.solution1;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.esdb.immutable.FunctionalTools.groupingByOrdered;
import static io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.esdb.immutable.solution1.GettingStateFromEventsTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GettingStateFromEventsTests extends EventStoreDBTest {
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

  static ShoppingCart getShoppingCart(EventStoreDBClient eventStore, String streamName) {
    // 1. Add logic here
   ShoppingCart shoppingCart = null;


    for (var event : getEvents(eventStore, streamName)) {
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

  static List<ShoppingCartEvent> getEvents(EventStoreDBClient eventStore, String streamName) {
    // 1. Add logic here
    try {
      return eventStore.readStream(streamName).get()
        .getEvents().stream()
        .map(GettingStateFromEventsTests::deserialize)
        .filter(ShoppingCartEvent.class::isInstance)
        .map(ShoppingCartEvent.class::cast)
        .toList();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() throws ExecutionException, InterruptedException {
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

    var streamName = "shopping_cart-%s".formatted(shoppingCartId);

    appendEvents(eventStore, streamName, events).get();

    var shoppingCart = getShoppingCart(eventStore, streamName);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(pairOfShoes, shoppingCart.productItems()[0]);
    assertEquals(tShirt, shoppingCart.productItems()[1]);
  }
}
