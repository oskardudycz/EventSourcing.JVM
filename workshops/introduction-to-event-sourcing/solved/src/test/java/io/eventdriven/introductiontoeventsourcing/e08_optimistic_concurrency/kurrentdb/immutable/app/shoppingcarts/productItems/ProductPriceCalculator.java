package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.productItems;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.immutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
    PricedProductItem calculate(ProductItem productItems);
}
