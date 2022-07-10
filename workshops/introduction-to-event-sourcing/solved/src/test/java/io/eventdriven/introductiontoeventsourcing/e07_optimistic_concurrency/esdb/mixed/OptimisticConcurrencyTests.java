package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed;

import com.eventstore.dbclient.WrongExpectedVersionException;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.ShoppingCartCommand;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.ShoppingCartStatus;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.ShoppingCartCommand.AddProductItemToShoppingCart;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.ShoppingCartCommand.OpenShoppingCart;
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
    int quantity
  ) {
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

    var entityStore = new EntityStore<ShoppingCart, ShoppingCartEvent, ShoppingCartCommand>(
      ShoppingCart.class,
      ShoppingCartEvent.class,
      this.eventStore,
      ShoppingCart::empty,
      "shopping_cart-%s"::formatted
    );

    // Open
    var openShoppingCart = new OpenShoppingCart(shoppingCartId, clientId);
    entityStore.add(
      openShoppingCart.shoppingCartId(),
      ShoppingCart.open(openShoppingCart.shoppingCartId(), openShoppingCart.clientId()).getKey()
    );

    // Add two pairs of shoes
    var addTwoPairsOfShoes = new AddProductItemToShoppingCart(shoppingCartId, twoPairsOfShoes);
    entityStore.getAndUpdate(
      addTwoPairsOfShoes.shoppingCartId(),
      addTwoPairsOfShoes,
      0L,
      (command, shoppingCart) ->
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), command.productItem())
    );

    // Add T-Shirt
    var exception = assertThrows(RuntimeException.class, () -> {
      var addTShirt = new AddProductItemToShoppingCart(shoppingCartId, tShirt);
      entityStore.getAndUpdate(
        addTShirt.shoppingCartId(),
        addTShirt,
        0L,
        (command, shoppingCart) ->
          shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), command.productItem())
      );
    });

    assertInstanceOf(ExecutionException.class, exception.getCause());
    assertInstanceOf(WrongExpectedVersionException.class, exception.getCause().getCause());

    var shoppingCart = entityStore.get(shoppingCartId);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(ShoppingCartStatus.Pending, shoppingCart.status());
    assertEquals(1, shoppingCart.productItems().length);

    assertEquals(pricedTwoPairOfShoes, shoppingCart.productItems()[0]);
  }
}
