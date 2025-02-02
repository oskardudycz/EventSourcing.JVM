package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.productItems;

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
