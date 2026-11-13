package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RandomProductPriceCalculatorTests {
  private final RandomProductPriceCalculator prices = new RandomProductPriceCalculator();

  @Test
  public void pricingTheSameProductAgainGivesTheSameUnitPrice() {
    var productId = UUID.randomUUID();

    var first = prices.calculate(new ProductItem(productId, 1));
    var second = prices.calculate(new ProductItem(productId, 3));

    assertThat(second.unitPrice()).isEqualTo(first.unitPrice());
  }
}
