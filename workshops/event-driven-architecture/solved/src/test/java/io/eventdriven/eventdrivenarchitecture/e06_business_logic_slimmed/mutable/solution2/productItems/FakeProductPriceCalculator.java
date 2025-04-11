package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution2.productItems.ProductItems.ProductItem;

public class FakeProductPriceCalculator implements ProductPriceCalculator {
  private final double value;

  private FakeProductPriceCalculator(double value) {
    this.value = value;
  }

  public static FakeProductPriceCalculator returning(double value) {
    return new FakeProductPriceCalculator(value);
  }

  public PricedProductItem calculate(ProductItem productItem) {
    return new PricedProductItem(productItem.productId(), productItem.quantity(), value);
  }
}
