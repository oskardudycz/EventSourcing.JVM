package io.eventdriven.ecommerce.shoppingcarts.pricing;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.api.ApiE2ESpecification.TestAction;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChangeablePrices implements ProductPriceCalculator {
    public static final double defaultUnitPrice = 100;

    private final Map<UUID, Double> prices = new ConcurrentHashMap<>();

    public TestStep priceOf(UUID productId, double unitPrice) {
        return (TestAction) () -> prices.put(productId, unitPrice);
    }

    @Override
    public PricedProductItem calculate(ProductItem productItem) {
        return new PricedProductItem(productItem, prices.getOrDefault(productItem.productId(), defaultUnitPrice));
    }
}
