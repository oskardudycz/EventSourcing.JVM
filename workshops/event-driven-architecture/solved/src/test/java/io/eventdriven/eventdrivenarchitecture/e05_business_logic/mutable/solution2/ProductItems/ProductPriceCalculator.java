package io.eventdriven.eventdrivenarchitecture.e05_business_logic.mutable.solution2.ProductItems;

import static io.eventdriven.eventdrivenarchitecture.e05_business_logic.mutable.solution2.ProductItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e05_business_logic.mutable.solution2.ProductItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
