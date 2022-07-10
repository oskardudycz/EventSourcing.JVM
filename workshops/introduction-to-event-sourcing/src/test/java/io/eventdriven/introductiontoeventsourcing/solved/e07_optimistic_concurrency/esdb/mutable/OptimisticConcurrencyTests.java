package io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.mutable;

import com.eventstore.dbclient.WrongExpectedVersionException;
import io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.mutable.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.mutable.BusinessLogic.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.mutable.BusinessLogic.ShoppingCartCommand.AddProductItemToShoppingCart;
import static io.eventdriven.introductiontoeventsourcing.solved.e07_optimistic_concurrency.esdb.mutable.BusinessLogic.ShoppingCartCommand.OpenShoppingCart;
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

  public static class PricedProductItem {
    private UUID productId;
    private double unitPrice;
    private int quantity;

    public PricedProductItem() {
    }

    public PricedProductItem(UUID productId, int quantity, double unitPrice) {
      this.setProductId(productId);
      this.setUnitPrice(unitPrice);
      this.setQuantity(quantity);
    }

    private double totalAmount() {
      return quantity() * unitPrice();
    }

    public UUID productId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public double unitPrice() {
      return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
      this.unitPrice = unitPrice;
    }

    public int quantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public void add(int quantity) {
      this.quantity += quantity;
    }

    public void subtract(int quantity) {
      this.quantity -= quantity;
    }
  }

  public static class ProductItem {
    private UUID productId;
    private int quantity;

    public ProductItem(UUID productId, int quantity) {
      this.setProductId(productId);
      this.setQuantity(quantity);
    }

    public UUID productId() {
      return productId;
    }

    public void setProductId(UUID productId) {
      this.productId = productId;
    }

    public int quantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }
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

    var entityStore = new EntityStore<>(
      ShoppingCart.class,
      ShoppingCartEvent.class,
      this.eventStore,
      "shopping_cart-%s"::formatted
    );

    // Open
    var openShoppingCart = new OpenShoppingCart(shoppingCartId, clientId);
    entityStore.add(ShoppingCart.open(openShoppingCart.shoppingCartId(), openShoppingCart.clientId()));

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
    assertEquals(BusinessLogic.ShoppingCartStatus.Pending, shoppingCart.status());
    assertEquals(1, shoppingCart.productItems().length);

    assertEquals(shoesId, shoppingCart.productItems()[0].productId());
    assertEquals(twoPairsOfShoes.quantity(), shoppingCart.productItems()[0].quantity());
    assertEquals(shoesPrice, shoppingCart.productItems()[0].unitPrice());
  }
}
