package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.entities.Aggregate;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.productItems.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

// ENTITY
public class ShoppingCart extends Aggregate<ShoppingCartEvent> {
  public enum Status {
    Pending,
    Confirmed,
    Canceled
  }

  private UUID id;
  private UUID clientId;
  private Status status;
  private List<PricedProductItem> productItems = new ArrayList<>();
  private OffsetDateTime confirmedAt;
  private OffsetDateTime canceledAt;

  public ShoppingCart(UUID id, UUID clientId, Status status, List<PricedProductItem> productItems, OffsetDateTime confirmedAt, OffsetDateTime canceledAt) {
    this.id = id;
    this.clientId = clientId;
    this.status = status;
    this.productItems = productItems;
    this.confirmedAt = confirmedAt;
    this.canceledAt = canceledAt;
  }

  public static ShoppingCart initial() {
    return new ShoppingCart();
  }

  private ShoppingCart() {
  }

  public static ShoppingCart open(UUID shoppingCartId, UUID clientId) {
    var shoppingCart = initial();

    shoppingCart.enqueue(new ShoppingCartOpened(shoppingCartId, clientId));

    return shoppingCart;
  }

  public void addProduct(
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

    var pricedProductItem = productPriceCalculator.calculate(productItem);

    enqueue(new ProductItemAddedToShoppingCart(
      id,
      pricedProductItem
    ));
  }

  public void removeProduct(
    PricedProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

    if (!hasEnough(productItem))
      throw new IllegalStateException("Not enough product items to remove");

    enqueue(new ProductItemRemovedFromShoppingCart(
      id,
      productItem
    ));
  }

  public void confirm() {
    if (isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartConfirmed(
      id,
      OffsetDateTime.now()
    ));
  }

  public void cancel() {
    if (isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartCanceled(
      id,
      OffsetDateTime.now()
    ));
  }

  private boolean isClosed() {
    return status == Status.Confirmed || status == Status.Canceled;
  }

  public boolean hasEnough(PricedProductItem productItem) {
    var currentQuantity = productItems.stream()
      .filter(pi -> pi.getProductId().equals(productItem.getProductId()))
      .mapToInt(PricedProductItem::getQuantity)
      .sum();

    return currentQuantity >= productItem.getQuantity();
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
    setStatus(Status.Pending);
    setProductItems(new ArrayList<>());
  }

  private void apply(ProductItemAddedToShoppingCart event) {
    var pricedProductItem = event.productItem();
    var productId = pricedProductItem.getProductId();
    var quantityToAdd = pricedProductItem.getQuantity();

    productItems.stream()
      .filter(pi -> pi.getProductId().equals(productId))
      .findAny()
      .ifPresentOrElse(
        current -> current.add(quantityToAdd),
        () -> productItems.add(pricedProductItem)
      );
  }

  private void apply(ProductItemRemovedFromShoppingCart event) {
    var pricedProductItem = event.productItem();
    var productId = pricedProductItem.getProductId();
    var quantityToRemove = pricedProductItem.getQuantity();

    productItems.stream()
      .filter(pi -> pi.getProductId().equals(productId))
      .findAny()
      .ifPresentOrElse(
        current -> current.subtract(quantityToRemove),
        () -> productItems.add(pricedProductItem)
      );
  }

  private void apply(ShoppingCartConfirmed event) {
    setStatus(Status.Confirmed);
    setConfirmedAt(event.confirmedAt());
  }

  private void apply(ShoppingCartCanceled event) {
    setStatus(Status.Canceled);
    setConfirmedAt(event.canceledAt());
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

  public Status status() {
    return status;
  }

  public void setStatus(Status status) {
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
}
