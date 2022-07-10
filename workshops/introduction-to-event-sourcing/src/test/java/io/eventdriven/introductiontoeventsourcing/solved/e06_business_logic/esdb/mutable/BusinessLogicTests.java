package io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.mutable;

import io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.mutable.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.mutable.BusinessLogic.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.tools.EventStoreDBTest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.solved.e06_business_logic.esdb.mutable.BusinessLogic.ShoppingCartCommand.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BusinessLogicTests extends EventStoreDBTest {
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

    private double totalPrice() {
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
    var pairOfShoes = new ProductItem(shoesId, 1);
    var tShirt = new ProductItem(tShirtId, 1);

    var shoesPrice = 100;
    var tShirtPrice = 50;

    var pricedPairOfShoes = new PricedProductItem(shoesId, 1, shoesPrice);
    var pricedTShirt = new PricedProductItem(tShirtId, 1, tShirtPrice);

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
      (command, shoppingCart) ->
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), command.productItem())
    );

    // Add T-Shirt
    var addTShirt = new AddProductItemToShoppingCart(shoppingCartId, tShirt);
    entityStore.getAndUpdate(
      addTShirt.shoppingCartId(),
      addTShirt,
      (command, shoppingCart) ->
        shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), command.productItem())
    );

    // Remove pair of shoes
    var removePairOfShoes = new RemoveProductItemFromShoppingCart(shoppingCartId, pricedPairOfShoes);
    entityStore.getAndUpdate(
      removePairOfShoes.shoppingCartId(),
      removePairOfShoes,
      (command, shoppingCart) ->
        shoppingCart.removeProduct(command.productItem())
    );

    // Confirm
    var confirmShoppingCart = new ConfirmShoppingCart(shoppingCartId);
    entityStore.getAndUpdate(
      confirmShoppingCart.shoppingCartId(),
      confirmShoppingCart,
      (command, shoppingCart) ->
        shoppingCart.confirm()
    );

    // Cancel
    assertThrows(IllegalStateException.class, () -> {
      var cancelShoppingCart = new CancelShoppingCart(shoppingCartId);
      entityStore.getAndUpdate(
        cancelShoppingCart.shoppingCartId(),
        cancelShoppingCart,
        (command, shoppingCart) ->
          shoppingCart.cancel()
      );
    });

    var shoppingCart = entityStore.get(shoppingCartId);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(BusinessLogic.ShoppingCartStatus.Confirmed, shoppingCart.status());
    assertEquals(2, shoppingCart.productItems().length);

    assertEquals(shoesId, shoppingCart.productItems()[0].productId());
    assertEquals(pairOfShoes.quantity(), shoppingCart.productItems()[0].quantity());
    assertEquals(shoesPrice, shoppingCart.productItems()[0].unitPrice());

    assertEquals(tShirtId, shoppingCart.productItems()[1].productId());
    assertEquals(tShirt.quantity(), shoppingCart.productItems()[1].quantity());
    assertEquals(tShirtPrice, shoppingCart.productItems()[1].unitPrice());
  }
}
