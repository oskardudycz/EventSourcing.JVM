package io.eventdriven.introductiontoeventsourcing.e02_getting_state_from_events.mutable;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e02_getting_state_from_events.mutable.GettingStateFromEventsTests.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GettingStateFromEventsTests {
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

  // ENTITY
  public static class ShoppingCart {
    private UUID id;
    private UUID clientId;
    private ShoppingCartStatus status;
    private List<PricedProductItem> productItems;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime canceledAt;

    public ShoppingCart(UUID id, UUID clientId, ShoppingCartStatus status, List<PricedProductItem> productItems, OffsetDateTime confirmedAt, OffsetDateTime canceledAt) {
      this.id = id;
      this.clientId = clientId;
      this.status = status;
      this.productItems = productItems;
      this.confirmedAt = confirmedAt;
      this.canceledAt = canceledAt;
    }

    public ShoppingCart() {
    }

    public UUID id() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public UUID clientId() {
      return clientId;
    }

    public void setClientId(UUID clientId) {
      this.clientId = clientId;
    }

    public ShoppingCartStatus status() {
      return status;
    }

    public void setStatus(ShoppingCartStatus status) {
      this.status = status;
    }

    public PricedProductItem[] productItems() {
      return productItems.toArray(PricedProductItem[]::new);
    }

    public void setProductItems(List<PricedProductItem> productItems) {
      this.productItems = productItems;
    }

    public OffsetDateTime confirmedAt() {
      return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
      this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime canceledAt() {
      return canceledAt;
    }

    public void setCanceledAt(OffsetDateTime canceledAt) {
      this.canceledAt = canceledAt;
    }

    public void evolve(ShoppingCartEvent event) {
      switch (event) {
        case ShoppingCartOpened opened -> apply(opened);
        case ProductItemAddedToShoppingCart productItemAdded ->
          apply(productItemAdded);
        case ProductItemRemovedFromShoppingCart productItemRemoved ->
          apply(productItemRemoved);
        case ShoppingCartConfirmed confirmed -> apply(confirmed);
        case ShoppingCartCanceled canceled -> apply(canceled);
      }
    }

    private void apply(ShoppingCartOpened event) {
      setId(event.shoppingCartId());
      setClientId(event.clientId());
      setStatus(ShoppingCartStatus.Pending);
      setProductItems(new ArrayList<>());
    }

    private void apply(ProductItemAddedToShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToAdd = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresentOrElse(
          current -> current.add(quantityToAdd),
          () -> productItems.add(pricedProductItem)
        );
    }

    private void apply(ProductItemRemovedFromShoppingCart event) {
      var pricedProductItem = event.productItem();
      var productId = pricedProductItem.productId();
      var quantityToRemove = pricedProductItem.quantity();

      productItems.stream()
        .filter(pi -> pi.productId().equals(productId))
        .findAny()
        .ifPresentOrElse(
          current -> current.subtract(quantityToRemove),
          () -> productItems.add(pricedProductItem)
        );
    }

    private void apply(ShoppingCartConfirmed event) {
      setStatus(ShoppingCartStatus.Confirmed);
      setConfirmedAt(event.confirmedAt());
    }

    private void apply(ShoppingCartCanceled event) {
      setStatus(ShoppingCartStatus.Canceled);
      setConfirmedAt(event.canceledAt());
    }
  }

  public enum ShoppingCartStatus {
    Pending,
    Confirmed,
    Canceled
  }

  static ShoppingCart getShoppingCart(ShoppingCartEvent[] events) {
    // 1. Add logic here
    var shoppingCart = new ShoppingCart();

    for (var event : events) {
      shoppingCart.evolve(event);
    }

    return shoppingCart;
  }

  @Test
  public void gettingState_ForSequenceOfEvents_ShouldSucceed() {
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

    var shoppingCart = getShoppingCart(events);

    assertEquals(shoppingCartId, shoppingCart.id());
    assertEquals(clientId, shoppingCart.clientId());
    assertEquals(2, shoppingCart.productItems().length);

    assertThat(shoppingCart.productItems()[0]).usingRecursiveComparison().isEqualTo(pairOfShoes);
    assertThat(shoppingCart.productItems()[1]).usingRecursiveComparison().isEqualTo(tShirt);
  }
}
