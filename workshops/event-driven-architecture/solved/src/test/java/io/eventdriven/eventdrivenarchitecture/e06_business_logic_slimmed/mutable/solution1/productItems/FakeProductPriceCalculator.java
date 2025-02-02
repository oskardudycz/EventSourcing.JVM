package io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution1.productItems;

import static io.eventdriven.eventdrivenarchitecture.e06_business_logic_slimmed.mutable.solution1.productItems.ProductItems.*;

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
