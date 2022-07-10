package io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1;

import io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1.BusinessLogic.FakeProductPriceCalculator;
import io.eventdriven.introductiontoeventsourcing.solved.e05_business_logic.mutable.solution1.BusinessLogic.ShoppingCart;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BusinessLogicTests {
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

  static BusinessLogic.ShoppingCart getShoppingCart(Object[] events) {
    // 1. Add logic here
    var shoppingCartEvents = Arrays.stream(events)
      .filter(ShoppingCartEvent.class::isInstance)
      .map(ShoppingCartEvent.class::cast)
      .toList();

    var shoppingCart = new ShoppingCart();

    for (var event : shoppingCartEvents) {
      shoppingCart.when(event);
    }

    return shoppingCart;
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

    var events = new ArrayList<>();

    // Open
    var shoppingCart = ShoppingCart.open(shoppingCartId, clientId);
    events.addAll(Arrays.stream(shoppingCart.dequeueUncommittedEvents()).toList());

    // Add two pairs of shoes
    shoppingCart = getShoppingCart(events.toArray());
    shoppingCart.addProduct(FakeProductPriceCalculator.returning(shoesPrice), twoPairsOfShoes);
    events.addAll(Arrays.stream(shoppingCart.dequeueUncommittedEvents()).toList());

    // Add T-Shirt
    shoppingCart = getShoppingCart(events.toArray());
    shoppingCart.addProduct(FakeProductPriceCalculator.returning(tShirtPrice), tShirt);
    events.addAll(Arrays.stream(shoppingCart.dequeueUncommittedEvents()).toList());

    // Remove pair of shoes
    // Hack alert!
    //
    // See that's why immutability is so cool, as it's predictable
    // As we're sharing objects (e.g. in PricedProductItem in events)
    // then adding them into list and changing it while appending/removing
    // then we can have unpleasant surprises.
    //
    // This will not likely happen if all objects are recreated (e.g. in the web requests)
    // However when it happens then it's tricky to diagnose.
    // Uncomment lines below and debug to find more.
    // shoppingCart = getShoppingCart(events.toArray());
    shoppingCart.removeProduct(pricedPairOfShoes);
    events.addAll(Arrays.stream(shoppingCart.dequeueUncommittedEvents()).toList());

    // Confirm
    // Uncomment line below and debug to find bug.
    // shoppingCart = getShoppingCart(events.toArray());
    shoppingCart.confirm();
    events.addAll(Arrays.stream(shoppingCart.dequeueUncommittedEvents()).toList());

    // Cancel
    ShoppingCart finalShoppingCart = shoppingCart;
    assertThrows(IllegalStateException.class, () -> {
      finalShoppingCart.cancel();
      events.addAll(Arrays.stream(finalShoppingCart.dequeueUncommittedEvents()).toList());
    });

    // Uncomment line below and debug to find bug.
    // shoppingCart = getShoppingCart(events.toArray());

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
