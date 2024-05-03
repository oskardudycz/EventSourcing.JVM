package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts.productItems;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
