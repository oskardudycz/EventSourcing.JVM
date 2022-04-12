package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.aggregates.AbstractAggregate;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

class ShoppingCart extends AbstractAggregate<UUID> {
  public UUID clientId() {
    return clientId;
  }

  public ProductItems productItems() {
    return productItems;
  }

  public ShoppingCartStatus status() {
    return status;
  }

  private final UUID clientId;
  private ProductItems productItems;
  private ShoppingCartStatus status;

  ShoppingCart(
    UUID id,
    UUID clientId,
    ProductItems productItems,
    ShoppingCartStatus status
  ) {
    this.id = id;
    this.clientId = clientId;
    this.productItems = productItems;
    this.status = status;
  }

  static ShoppingCart open(UUID shoppingCartId, UUID clientId) {
    var shoppingCart = new ShoppingCart(
      shoppingCartId,
      clientId,
      ProductItems.empty(),
      ShoppingCartStatus.Pending
    );
    shoppingCart.enqueue(new ShoppingCartOpened(shoppingCart.id, shoppingCart.clientId));

    return shoppingCart;
  }

  void addProductItem(
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

    var pricedProductItem = productPriceCalculator.calculate(productItem);

    productItems = productItems.add(pricedProductItem);

    enqueue(new ProductItemAddedToShoppingCart(
      id,
      pricedProductItem
    ));
  }

  void removeProductItem(
    PricedProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

    productItems.assertThatCanRemove(productItem);

    enqueue(new ProductItemRemovedFromShoppingCart(
      id,
      productItem
    ));
  }

  void confirm() {
    if (isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartConfirmed(
      id,
      LocalDateTime.now()
    ));
  }

  void cancel() {
    if (isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartCanceled(
      id,
      LocalDateTime.now()
    ));
  }

  private boolean isClosed() {
    return this.status.isClosed();
  }

  static String mapToStreamId(UUID shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId);
  }

  static ShoppingCart when(@Nullable ShoppingCart current, ShoppingCartEvent event) {
    switch (event) {
      case ShoppingCartOpened shoppingCartOpened:
        return new ShoppingCart(
          shoppingCartOpened.shoppingCartId(),
          shoppingCartOpened.clientId(),
          ProductItems.empty(),
          ShoppingCartStatus.Pending
        );
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
        current.productItems =
          current.productItems.add(productItemAddedToShoppingCart.productItem());
        break;
      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
        current.productItems =
          current.productItems.remove(productItemRemovedFromShoppingCart.productItem());
        break;
      case ShoppingCartConfirmed shoppingCartConfirmed:
        current.status = ShoppingCartStatus.Confirmed;
        break;
      case ShoppingCartCanceled shoppingCartCanceled:
        current.status = ShoppingCartStatus.Canceled;
        break;
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    }

    return current;
  }
}
