package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCart extends AbstractAggregate<ShoppingCartEvent, ShoppingCartId> {
  public UUID clientId() {
    return clientId;
  }

  public PricedProductItem[] productItems() {
    return productItems.items().toArray(new PricedProductItem[0]);
  }

  public double totalPrice() {
    return productItems.items().stream()
      .mapToDouble(PricedProductItem::totalPrice)
      .sum();
  }
  private UUID clientId;
  private ProductItems productItems;
  private ShoppingCartStatus status;

  private ShoppingCart() {
  }

  public static ShoppingCart empty() {
    return new ShoppingCart();
  }

  void open(ShoppingCartId shoppingCartId, UUID clientId) {
    if (status != null)
      return;

    enqueue(new ShoppingCartOpened(shoppingCartId, clientId));
  }

  void addProductItem(
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem
  ) {
    if (isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

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
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

    productItems.assertThatCanRemove(productItem);

    enqueue(new ProductItemRemovedFromShoppingCart(
      id,
      productItem
    ));
  }

  void confirm(OffsetDateTime now) {
    if (isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartConfirmed(
      id,
      now
    ));
  }

  void cancel(OffsetDateTime now) {
    if (isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(status));

    enqueue(new ShoppingCartCanceled(
      id,
      now
    ));
  }

  private boolean isClosed() {
    return this.status == null || this.status.isClosed();
  }

  @Override
  public void evolve(ShoppingCartEvent event) {
    switch (event) {
      case ShoppingCartOpened shoppingCartOpened -> {
        id = shoppingCartOpened.shoppingCartId();
        clientId = shoppingCartOpened.clientId();
        productItems = ProductItems.empty();
        status = ShoppingCartStatus.Pending;
      }
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart ->
        productItems = productItems.add(productItemAddedToShoppingCart.productItem());

      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart ->
        productItems = productItems.remove(productItemRemovedFromShoppingCart.productItem());

      case ShoppingCartConfirmed ignored ->
        status = ShoppingCartStatus.Confirmed;

      case ShoppingCartCanceled ignored ->
        status = ShoppingCartStatus.Canceled;
    }
  }
}
