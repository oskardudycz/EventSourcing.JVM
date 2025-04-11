package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution1.productItems;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution1.productItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
