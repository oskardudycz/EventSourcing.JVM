package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.shoppingcarts.productItems;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.shoppingcarts.productItems.ProductPriceCalculator;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

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
