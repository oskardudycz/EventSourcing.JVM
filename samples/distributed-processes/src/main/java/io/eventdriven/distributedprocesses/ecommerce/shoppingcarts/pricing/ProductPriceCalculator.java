package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItem);
}
