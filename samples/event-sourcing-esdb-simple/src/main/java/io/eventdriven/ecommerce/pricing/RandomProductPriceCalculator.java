package io.eventdriven.ecommerce.pricing;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RandomProductPriceCalculator {
  private final Map<UUID, Double> productPrices = new HashMap<>();

  public PricedProductItem Calculate(ProductItem productItem) {
    var random = new Random();

    var price = productPrices.putIfAbsent(
      productItem.productId(),
      random.nextDouble()
    );

    return PricedProductItem.From(productItem, price);
  }
}
