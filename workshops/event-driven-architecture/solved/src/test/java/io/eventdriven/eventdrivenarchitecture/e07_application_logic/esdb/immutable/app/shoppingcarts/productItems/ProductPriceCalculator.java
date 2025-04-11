package io.eventdriven.eventdrivenarchitecture.e07_application_logic.esdb.immutable.app.shoppingcarts.productItems;

import static io.eventdriven.eventdrivenarchitecture.e07_application_logic.esdb.immutable.app.shoppingcarts.productItems.ProductItems.*;

public interface ProductPriceCalculator {
  PricedProductItem calculate(ProductItem productItems);
}
