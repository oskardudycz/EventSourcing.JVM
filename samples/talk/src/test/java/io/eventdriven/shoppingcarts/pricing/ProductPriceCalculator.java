package io.eventdriven.shoppingcarts.pricing;


import io.eventdriven.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.shoppingcarts.productitems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItem);
}
