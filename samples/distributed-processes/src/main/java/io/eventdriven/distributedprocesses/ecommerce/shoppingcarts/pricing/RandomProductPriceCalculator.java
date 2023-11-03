package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RandomProductPriceCalculator implements ProductPriceCalculator {
  private final Map<UUID, Double> productPrices = new HashMap<>();

  public PricedProductItem calculate(ProductItem productItem) {
    var random = new Random();

    var price = random.nextDouble() * 100;

    productPrices.putIfAbsent(productItem.productId(), price);

    return new PricedProductItem(productItem, price);
  }
}
