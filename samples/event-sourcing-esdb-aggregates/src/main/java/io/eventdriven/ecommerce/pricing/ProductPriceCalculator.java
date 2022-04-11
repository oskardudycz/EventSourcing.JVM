package io.eventdriven.ecommerce.pricing;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItem);
}
