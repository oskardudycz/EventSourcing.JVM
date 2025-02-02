package io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems.ProductItem;

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
