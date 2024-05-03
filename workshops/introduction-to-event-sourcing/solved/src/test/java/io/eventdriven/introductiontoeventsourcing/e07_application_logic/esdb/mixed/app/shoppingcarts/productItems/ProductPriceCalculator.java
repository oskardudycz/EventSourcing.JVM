package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.productItems;

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.productItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
