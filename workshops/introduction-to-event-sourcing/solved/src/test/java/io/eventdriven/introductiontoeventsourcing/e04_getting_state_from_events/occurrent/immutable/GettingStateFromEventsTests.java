package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.occurrent.immutable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.occurrent.application.converter.CloudEventConverter;
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter;
import org.occurrent.dsl.query.blocking.DomainEventQueries;
import org.occurrent.eventstore.inmemory.InMemoryEventStore;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.occurrent.immutable.GettingStateFromEventsTests.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.occurrent.filter.Filter.streamId;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class GettingStateFromEventsTests {

  private DomainEventQueries<ShoppingCartEvent> eventQueries;

  private InMemoryEventStore eventStore;
  private CloudEventConverter<ShoppingCartEvent> cloudEventConverter;

  @BeforeEach
  void setup() {
    var objectMapper = new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    eventStore = new InMemoryEventStore();
    cloudEventConverter = new JacksonCloudEventConverter<>(objectMapper, URI.create("urn:eventsourcing:jvm:samples:occurrent"));
    eventQueries = new DomainEventQueries<>(eventStore, cloudEventConverter);
  }

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

  public record ProductItems(
    PricedProductItem[] values
  ) {
    public static ProductItems empty() {
      return new ProductItems(new PricedProductItem[]{});
    }

    public ProductItems add(PricedProductItem productItem) {
      return new ProductItems(
        StreamEx.of(values)
          .append(productItem)
          .groupingBy(PricedProductItem::productId)
          .entrySet().stream()
          .map(group -> group.getValue().size() == 1 ?
            group.getValue().get(0) :
            new PricedProductItem(
              group.getKey(),
              group.getValue().stream().mapToInt(PricedProductItem::quantity).sum(),
              group.getValue().get(0).unitPrice()
            )
          )
          .toArray(PricedProductItem[]::new)
      );
    }

    public ProductItems remove(PricedProductItem productItem) {
      return new ProductItems(
        Arrays.stream(values())
          .map(pi -> pi.productId().equals(productItem.productId()) ?
            new PricedProductItem(
              pi.productId(),
              pi.quantity() - productItem.quantity(),
              pi.unitPrice()
            )
            : pi
          )
          .filter(pi -> pi.quantity > 0)
          .toArray(PricedProductItem[]::new)
      );
    }
  }

  // ENTITY
  sealed public interface ShoppingCart {
    UUID id();

    UUID clientId();

    ProductItems productItems();

    record PendingShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems
    ) implements ShoppingCart {
    }

    record ConfirmedShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems,
      OffsetDateTime confirmedAt
    ) implements ShoppingCart {
    }

    record CanceledShoppingCart(
      UUID id,
      UUID clientId,
      ProductItems productItems,
      OffsetDateTime canceledAt
    ) implements ShoppingCart {
    }

    default ShoppingCartStatus status() {
      return switch (this) {
        case PendingShoppingCart ignored:
          yield ShoppingCartStatus.Pending;
        case ConfirmedShoppingCart ignored:
          yield ShoppingCartStatus.Confirmed;
        case CanceledShoppingCart ignored:
          yield ShoppingCartStatus.Canceled;
      };
    }

    static ShoppingCart when(ShoppingCart current, ShoppingCartEvent event) {
      return switch (event) {
        case ShoppingCartOpened shoppingCartOpened:
          yield new PendingShoppingCart(
            shoppingCartOpened.shoppingCartId(),
            shoppingCartOpened.clientId(),
            ProductItems.empty()
          );
        case ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
          yield new PendingShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems().add(productItemAddedToShoppingCart.productItem())
          );
        case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
          yield new PendingShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems().remove(productItemRemovedFromShoppingCart.productItem())
          );
        case ShoppingCartConfirmed shoppingCartConfirmed:
          yield new ConfirmedShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems(),
            shoppingCartConfirmed.confirmedAt()
          );
        case ShoppingCartCanceled shoppingCartCanceled:
          yield new CanceledShoppingCart(
            current.id(),
            current.clientId(),
            current.productItems(),
            shoppingCartCanceled.canceledAt()
          );
      };
    }

    static ShoppingCart empty() {
      return new PendingShoppingCart(null, null, null);
    }
  }

  public enum ShoppingCartStatus {
    Pending,
    Confirmed,
    Canceled
  }

  ShoppingCart getShoppingCart(String streamName) {
    // 1. Add logic here
    return StreamEx.of(eventQueries.query(streamId(streamName)))
      .foldLeft(ShoppingCart.empty(), ShoppingCart::when);
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

    var events = Stream.of(
      new ShoppingCartOpened(shoppingCartId, clientId),
      new ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes),
      new ProductItemAddedToShoppingCart(shoppingCartId, tShirt),
      new ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes),
      new ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()),
      new ShoppingCartCanceled(shoppingCartId, OffsetDateTime.now())
    ).map(cloudEventConverter::toCloudEvent);

    var streamName = "shopping_cart-%s".formatted(shoppingCartId);

    eventStore.write(streamName, events);

    var shoppingCart = getShoppingCart(streamName);

    assertAll(
      () -> assertThat(shoppingCart.id()).isEqualTo(shoppingCartId),
      () -> assertThat(shoppingCart.clientId()).isEqualTo(clientId),
      () -> assertThat(shoppingCart.productItems().values).hasSize(2),
      () -> assertThat(shoppingCart.productItems().values()).startsWith(pairOfShoes, tShirt)
    );
  }
}
