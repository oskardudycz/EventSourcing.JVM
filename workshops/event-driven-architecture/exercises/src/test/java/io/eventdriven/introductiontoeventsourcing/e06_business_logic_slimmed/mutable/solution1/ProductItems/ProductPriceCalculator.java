package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution1.ProductItems;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution1.ProductItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
