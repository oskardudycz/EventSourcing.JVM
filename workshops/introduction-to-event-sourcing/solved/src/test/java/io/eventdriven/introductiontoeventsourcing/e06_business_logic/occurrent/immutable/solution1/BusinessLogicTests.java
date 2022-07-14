package io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution1;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.occurrent.application.converter.CloudEventConverter;
import org.occurrent.application.converter.jackson.JacksonCloudEventConverter;
import org.occurrent.application.service.blocking.ApplicationService;
import org.occurrent.application.service.blocking.generic.GenericApplicationService;
import org.occurrent.dsl.query.blocking.DomainEventQueries;
import org.occurrent.eventstore.inmemory.InMemoryEventStore;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution1.BusinessLogic.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution1.BusinessLogic.ShoppingCartCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.occurrent.immutable.solution1.BusinessLogicTests.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.occurrent.filter.Filter.streamId;

public class BusinessLogicTests {
  private DomainEventQueries<ShoppingCartEvent> eventQueries;

  private InMemoryEventStore eventStore;
  private CloudEventConverter<ShoppingCartEvent> cloudEventConverter;
  private ApplicationService<ShoppingCartEvent> applicationService;

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
    applicationService = new GenericApplicationService<>(eventStore, cloudEventConverter);
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

  public record ProductItem(
    UUID productId,
    int quantity) {
  }

  public record ProductItems(
    PricedProductItem[] values
  ) {
    public static ProductItems empty() {
      return new ProductItems(new PricedProductItem[]{});
    }

    public ProductItems add(PricedProductItem productItem) {
      return new ProductItems(
        StreamEx.of(values).append(productItem)
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

    public boolean hasEnough(PricedProductItem productItem) {
      var currentQuantity = Arrays.stream(values)
        .filter(pi -> pi.productId().equals(productItem.productId()))
        .mapToInt(PricedProductItem::quantity)
        .sum();

      return currentQuantity >= productItem.quantity();
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

    default boolean isClosed() {
      return this instanceof ConfirmedShoppingCart || this instanceof CanceledShoppingCart;
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

    var handle =
      CommandHandler.<ShoppingCart, ShoppingCartEvent, ShoppingCartCommand>commandHandler(
        applicationService,
        ShoppingCart::empty,
        "shopping_cart-%s"::formatted,
        ShoppingCart::when,
        (command, entity) -> ShoppingCartCommandHandler.decide(
          () -> command instanceof AddProductItemToShoppingCart addProduct ?
            FakeProductPriceCalculator.returning(addProduct.productItem() == twoPairsOfShoes ? shoesPrice : tShirtPrice)
            : null,
          command,
          entity
        )
      );


    // Open
    var openShoppingCart = new OpenShoppingCart(shoppingCartId, clientId);
    handle.accept(openShoppingCart.shoppingCartId(), openShoppingCart);


    // Add two pairs of shoes
    var addTwoPairsOfShoes = new AddProductItemToShoppingCart(shoppingCartId, twoPairsOfShoes);
    handle.accept(addTwoPairsOfShoes.shoppingCartId(), addTwoPairsOfShoes);

    // Add T-Shirt
    var addTShirt = new AddProductItemToShoppingCart(shoppingCartId, tShirt);
    handle.accept(addTShirt.shoppingCartId(), addTShirt);

    // Remove pair of shoes
    var removePairOfShoes = new RemoveProductItemFromShoppingCart(shoppingCartId, pricedPairOfShoes);
    handle.accept(removePairOfShoes.shoppingCartId(), removePairOfShoes);

    // Confirm
    var confirmShoppingCart = new ConfirmShoppingCart(shoppingCartId);
    handle.accept(confirmShoppingCart.shoppingCartId(), confirmShoppingCart);

    // Cancel
    assertThrows(IllegalStateException.class, () -> {
      var cancelShoppingCart = new CancelShoppingCart(shoppingCartId);
      handle.accept(cancelShoppingCart.shoppingCartId(), cancelShoppingCart);
    });


    var shoppingCart = StreamEx.of(eventQueries.query(streamId("shopping_cart-%s".formatted(shoppingCartId))))
      .foldLeft(ShoppingCart.empty(), ShoppingCart::when);

    assertAll(
      () -> assertThat(shoppingCart.id()).isEqualTo(shoppingCartId),
      () -> assertThat(shoppingCart.clientId()).isEqualTo(clientId),
      () -> assertThat(shoppingCart.productItems().values()).hasSize(2),
      () -> assertThat(shoppingCart.status()).isEqualTo(ShoppingCartStatus.Confirmed),
      () -> assertThat(shoppingCart.productItems().values()).startsWith(pricedPairOfShoes, pricedTShirt)
    );
  }
}
