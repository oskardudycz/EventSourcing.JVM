package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.shoppingcarts.productItems.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.shoppingcarts.productItems.ProductItems.ProductItem;

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


  public static ShoppingCart initial() {
    return new ShoppingCart();
  }

  private ShoppingCart() {
  }

  private ShoppingCart(
    ShoppingCartOpened event
  ) {
    evolve(event);
  }

  public static Entry<ShoppingCartEvent, ShoppingCart> open(UUID shoppingCartId, UUID clientId) {
    var event = new ShoppingCartOpened(
      shoppingCartId,
      clientId
    );

    return new SimpleImmutableEntry<>(event, new ShoppingCart(event));
  }

  public ProductItemAddedToShoppingCart addProduct(
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

    var pricedProductItem = productPriceCalculator.calculate(productItem);

    return apply(new ProductItemAddedToShoppingCart(
      id,
      pricedProductItem
    ));
  }

  public ProductItemRemovedFromShoppingCart removeProduct(
    PricedProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

    if (!hasEnough(productItem))
      throw new IllegalStateException("Not enough product items to remove");

    return apply(new ProductItemRemovedFromShoppingCart(
      id,
      productItem
    ));
  }

  public ShoppingCartConfirmed confirm() {
    if (isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

    return apply(new ShoppingCartConfirmed(
      id,
      OffsetDateTime.now()
    ));
  }

  public ShoppingCartCanceled cancel() {
    if (isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

    return apply(new ShoppingCartCanceled(
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

  public UUID id() {
    return id;
  }

  public UUID clientId() {
    return clientId;
  }

  public Status status() {
    return status;
  }

  public PricedProductItem[] productItems() {
    return productItems.toArray(PricedProductItem[]::new);
  }

  public OffsetDateTime confirmedAt() {
    return confirmedAt;
  }

  public OffsetDateTime canceledAt() {
    return canceledAt;
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
    id = event.shoppingCartId();
    clientId = event.clientId();
    status = Status.Pending;
    productItems = new ArrayList<>();
  }

  private ProductItemAddedToShoppingCart apply(ProductItemAddedToShoppingCart event) {
    var pricedProductItem = event.productItem();
    var productId = pricedProductItem.getProductId();
    var quantityToAdd = pricedProductItem.getQuantity();

    productItems.stream()
      .filter(pi -> pi.getProductId().equals(productId))
      .findAny()
      .ifPresentOrElse(
        current -> productItems.set(
          productItems.indexOf(current),
          new PricedProductItem(current.getProductId(), current.getQuantity() + quantityToAdd, current.getUnitPrice())
        ),
        () -> productItems.add(pricedProductItem)
      );
    return event;
  }

  private ProductItemRemovedFromShoppingCart apply(ProductItemRemovedFromShoppingCart event) {
    var pricedProductItem = event.productItem();
    var productId = pricedProductItem.getProductId();
    var quantityToRemove = pricedProductItem.getQuantity();

    productItems.stream()
      .filter(pi -> pi.getProductId().equals(productId))
      .findAny()
      .ifPresent(
        current -> productItems.set(
          productItems.indexOf(current),
          new PricedProductItem(current.getProductId(), current.getQuantity() - quantityToRemove, current.getUnitPrice())
        )
      );

    return event;
  }

  private ShoppingCartConfirmed apply(ShoppingCartConfirmed event) {
    status = Status.Confirmed;
    confirmedAt = event.confirmedAt();
    return event;
  }

  private ShoppingCartCanceled apply(ShoppingCartCanceled event) {
    status = Status.Canceled;
    canceledAt = event.canceledAt();
    return event;
  }
}
