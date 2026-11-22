package io.eventdriven.ecommerce.shoppingcarts.pricing;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

public interface ProductPriceCalculator {
    PricedProductItem calculate(ProductItem productItem);
}
