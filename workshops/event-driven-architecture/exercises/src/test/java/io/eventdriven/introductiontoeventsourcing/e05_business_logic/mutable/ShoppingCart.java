package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ProductItems.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ProductItems.ProductItems.*;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ShoppingCartEvent.*;

// ENTITY
public class ShoppingCart {
  public enum Status {
    Pending,
    Confirmed,
    Canceled
  }

  private UUID id;
  private UUID clientId;
  private Status status;
  private List<PricedProductItem> productItems;
  private OffsetDateTime confirmedAt;
  private OffsetDateTime canceledAt;
  private final List<Object> uncommittedEvents = List.of();

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
    throw new RuntimeException("Fill the implementation part");
  }

  void addProduct(
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem
  ) {
    throw new RuntimeException("Fill the implementation part");
  }

  void removeProduct(
    PricedProductItem productItem
  ) {
    throw new RuntimeException("Fill the implementation part");
  }

  void confirm() {
    throw new RuntimeException("Fill the implementation part");
  }

  void cancel() {
    throw new RuntimeException("Fill the implementation part");
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

  public Object[] uncommittedEvents() {
    return uncommittedEvents.toArray();
  }
}
