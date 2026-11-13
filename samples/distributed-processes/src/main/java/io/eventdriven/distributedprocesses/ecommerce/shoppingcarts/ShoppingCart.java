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
  private ProductItems productItems = ProductItems.empty();
  private ShoppingCartStatus status;

  private ShoppingCart() {
  }

  public static ShoppingCart empty() {
    return new ShoppingCart();
  }

  void addProductItem(
    ShoppingCartId shoppingCartId,
    UUID clientId,
    ProductPriceCalculator productPriceCalculator,
    ProductItem productItem,
    OffsetDateTime now
  ) {
    if (status != null && status.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(status));

    var unitPrice = productItems.find(productItem.productId())
      .map(PricedProductItem::unitPrice)
      .orElseGet(() -> productPriceCalculator.calculate(productItem).unitPrice());

    enqueue(new ProductItemAddedToShoppingCart(
      shoppingCartId,
      clientId,
      new PricedProductItem(productItem, unitPrice),
      now
    ));
  }

  void removeProductItem(
    ProductItem productItem,
    OffsetDateTime now
  ) {
    if (isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(status));

    var pricedProductItem = productItems.find(productItem.productId())
      .map(current -> new PricedProductItem(productItem, current.unitPrice()))
      .orElseThrow(() -> new IllegalStateException("Product item wasn't found"));

    productItems.assertThatCanRemove(pricedProductItem);

    enqueue(new ProductItemRemovedFromShoppingCart(
      id,
      pricedProductItem,
      now
    ));
  }

  void checkOut(ProductPriceCalculator productPriceCalculator, OffsetDateTime now) {
    if (status == ShoppingCartStatus.CheckedOut)
      return;

    if (isClosed())
      throw new IllegalStateException("Checking out cart in '%s' status is not allowed.".formatted(status));

    if (productItems.items().isEmpty())
      throw new IllegalStateException("Checking out an empty cart is not allowed.");

    var priceChanges = productItems.items().stream()
      .map(current -> new ProductItemPriceChanged(
        id,
        current.productId(),
        current.unitPrice(),
        productPriceCalculator.calculate(current.productItem()).unitPrice(),
        now
      ))
      .filter(change -> change.newUnitPrice() != change.oldUnitPrice())
      .toList();

    if (!priceChanges.isEmpty()) {
      priceChanges.forEach(this::enqueue);
      return;
    }

    enqueue(new ShoppingCartCheckedOut(
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
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart -> {
        if (status == null) {
          id = productItemAddedToShoppingCart.shoppingCartId();
          status = ShoppingCartStatus.Pending;
        }
        productItems = productItems.add(productItemAddedToShoppingCart.productItem());
      }

      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart ->
        productItems = productItems.remove(productItemRemovedFromShoppingCart.productItem());

      case ProductItemPriceChanged productItemPriceChanged ->
        productItems = productItems.changeUnitPrice(
          productItemPriceChanged.productId(),
          productItemPriceChanged.newUnitPrice()
        );

      case ShoppingCartCheckedOut ignored ->
        status = ShoppingCartStatus.CheckedOut;
    }
  }
}
