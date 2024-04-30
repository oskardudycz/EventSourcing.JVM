package io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1.ProductItems;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.mutable.solution1.ProductItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
