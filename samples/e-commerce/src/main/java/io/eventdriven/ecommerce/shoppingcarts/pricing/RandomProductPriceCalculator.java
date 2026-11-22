package io.eventdriven.ecommerce.shoppingcarts.pricing;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RandomProductPriceCalculator implements ProductPriceCalculator {
    private final Map<UUID, Double> productPrices = new ConcurrentHashMap<>();

    public PricedProductItem calculate(ProductItem productItem) {
        var price = productPrices.computeIfAbsent(
                productItem.productId(),
                ignored -> (1 + ThreadLocalRandom.current().nextInt(9999)) / 100.0);

        return new PricedProductItem(productItem, price);
    }
}
