package io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems;

import static io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.PricedProductItem;
import static io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.immutable.app.shoppingcarts.productItems.ProductItems.ProductItem;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
