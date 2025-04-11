package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.app.shoppingcarts.productItems;

import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
