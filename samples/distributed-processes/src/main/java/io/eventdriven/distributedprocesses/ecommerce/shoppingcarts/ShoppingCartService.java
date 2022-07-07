package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public class ShoppingCartService {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> store;
  private final ProductPriceCalculator productPriceCalculator;

  public ShoppingCartService(
    AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> entityStore,
    ProductPriceCalculator productPriceCalculator) {
    this.store = entityStore;
    this.productPriceCalculator = productPriceCalculator;
  }

  public ETag open(UUID shoppingCartId, UUID clientId) {
    return store.add(ShoppingCart.open(shoppingCartId, clientId));
  }

  public ETag addProductItem(UUID shoppingCartId, ProductItem productItem, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.addProductItem(productPriceCalculator, productItem),
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag removeProductItem(UUID shoppingCartId, PricedProductItem productItem, Long expectedVersion) {
    return store.getAndUpdate(
      current -> current.removeProductItem(productItem),
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag confirm(UUID shoppingCartId, Long expectedVersion) {
    return store.getAndUpdate(
      ShoppingCart::confirm,
      shoppingCartId,
      expectedVersion
    );
  }

  public ETag cancel(UUID shoppingCartId, Long expectedVersion) {
    return store.getAndUpdate(
      ShoppingCart::cancel,
      shoppingCartId,
      expectedVersion
    );
  }
}
