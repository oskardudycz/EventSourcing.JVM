package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ProductItems;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.ProductItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
