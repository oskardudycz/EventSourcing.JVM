package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.pricing;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItem);
}
