package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mixed.app.shoppingcarts.productItems;

import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mixed.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mixed.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
