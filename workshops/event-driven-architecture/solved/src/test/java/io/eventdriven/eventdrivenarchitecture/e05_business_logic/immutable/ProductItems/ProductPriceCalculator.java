package io.eventdriven.eventdrivenarchitecture.e05_business_logic.immutable.ProductItems;

import static io.eventdriven.eventdrivenarchitecture.e05_business_logic.immutable.ProductItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e05_business_logic.immutable.ProductItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
