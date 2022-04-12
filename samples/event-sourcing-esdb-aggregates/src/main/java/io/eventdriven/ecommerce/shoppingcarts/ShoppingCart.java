package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.aggregates.AbstractAggregate;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;

import java.time.LocalDateTime;
import java.util.UUID;

class ShoppingCart extends AbstractAggregate<ShoppingCartEvent, UUID> {
  UUID clientId() {
    return clientId;
  }

  public ProductItems productItems() {
    return productItems;
  }

  ShoppingCartStatus status() {
    return status;
  }

  private UUID clientId;
  private ProductItems productItems;
  private ShoppingCartStatus status;

  private ShoppingCart(){

  }

  public static ShoppingCart empty(){
    return new ShoppingCart();
  }

  ShoppingCart(
    UUID id,
    UUID clientId
  ) {
    this.id = id;
    this.clientId = clientId;

    enqueue(new ShoppingCartOpened(id, clientId));
  }

  static ShoppingCart open(UUID shoppingCartId, UUID clientId) {
   return new ShoppingCart(
     shoppingCartId,
     clientId
   );
  }

  void addProductItem(
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

  @Override
  public void when(ShoppingCartEvent event) {
    switch (event) {
      case ShoppingCartOpened shoppingCartOpened:
        id = shoppingCartOpened.shoppingCartId();
        clientId = shoppingCartOpened.clientId();
        productItems = ProductItems.empty();
        status = ShoppingCartStatus.Pending;
        break;
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
        productItems = productItems.add(productItemAddedToShoppingCart.productItem());
        break;
      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
        productItems = productItems.remove(productItemRemovedFromShoppingCart.productItem());
        break;
      case ShoppingCartConfirmed ignored:
        status = ShoppingCartStatus.Confirmed;
        break;
      case ShoppingCartCanceled ignored:
        status = ShoppingCartStatus.Canceled;
        break;
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    }
  }
}
