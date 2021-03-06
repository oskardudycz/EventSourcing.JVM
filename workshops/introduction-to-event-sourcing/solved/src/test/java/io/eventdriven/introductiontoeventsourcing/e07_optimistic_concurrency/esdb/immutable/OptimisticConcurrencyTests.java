package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable;

import com.eventstore.dbclient.WrongExpectedVersionException;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.BusinessLogic.*;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.BusinessLogic.ShoppingCartCommand.AddProductItemToShoppingCart;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.BusinessLogic.ShoppingCartCommand.OpenShoppingCart;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.FunctionalTools.FoldLeft.foldLeft;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.FunctionalTools.groupingByOrdered;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.OptimisticConcurrencyTests.ShoppingCartEvent.*;
import static org.junit.jupiter.api.Assertions.*;

public class OptimisticConcurrencyTests extends EventStoreDBTest {
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
        Stream.concat(Arrays.stream(values), Stream.of(productItem))
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

  static ShoppingCart getShoppingCart(Object[] events) {
    // 1. Add logic here
    return Arrays.stream(events)
      .filter(ShoppingCartEvent.class::isInstance)
      .map(ShoppingCartEvent.class::cast)
      .collect(foldLeft(ShoppingCart::empty, ShoppingCart::when));
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() {
    var shoppingCartId = UUID.randomUUID();
    var clientId = UUID.randomUUID();
    var shoesId = UUID.randomUUID();
    var tShirtId = UUID.randomUUID();

    var twoPairsOfShoes = new ProductItem(shoesId, 2);
    var tShirt = new ProductItem(tShirtId, 1);

    var shoesPrice = 100;
    var tShirtPrice = 50;

    var pricedTwoPairOfShoes = new PricedProductItem(shoesId, 2, shoesPrice);

    var handle =
      io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.EntityStore.<ShoppingCart, ShoppingCartEvent, ShoppingCartCommand>commandHandler(
        ShoppingCartEvent.class,
        eventStore,
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
    handle.accept(openShoppingCart.shoppingCartId(), openShoppingCart, null);

    // Add two pairs of shoes
    var addTwoPairsOfShoes = new AddProductItemToShoppingCart(shoppingCartId, twoPairsOfShoes);
    handle.accept(addTwoPairsOfShoes.shoppingCartId(), addTwoPairsOfShoes, 0L);

    // Add T-Shirt
    var exception = assertThrows(RuntimeException.class, () -> {
      var addTShirt = new AddProductItemToShoppingCart(shoppingCartId, tShirt);
      handle.accept(addTShirt.shoppingCartId(), addTShirt, 0L);
    });

    assertInstanceOf(ExecutionException.class, exception.getCause());
    assertInstanceOf(WrongExpectedVersionException.class, exception.getCause().getCause());

    var getResult = EntityStore.get(
      ShoppingCartEvent.class,
      eventStore,
      ShoppingCart::when,
      ShoppingCart::empty,
      "shopping_cart-%s".formatted(shoppingCartId)
    );

    assertTrue(getResult.isPresent());

    var shoppingCart = getResult.get();

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(1, shoppingCart.productItems().values().length);
    assertEquals(ShoppingCartStatus.Pending, shoppingCart.status());

    assertEquals(pricedTwoPairOfShoes, shoppingCart.productItems().values()[0]);
  }
}
