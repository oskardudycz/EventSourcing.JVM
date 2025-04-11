package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
