package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class RandomProductPriceCalculatorTests {
    private final RandomProductPriceCalculator prices = new RandomProductPriceCalculator();

    @Test
    public void pricingTheSameProductAgainGivesTheSameUnitPrice() {
        var productId = UUID.randomUUID();

        var first = prices.calculate(new ProductItem(productId, 1));
        var second = prices.calculate(new ProductItem(productId, 3));

        assertThat(second.unitPrice()).isEqualTo(first.unitPrice());
    }

    @Test
    public void unitPricesAreInWholeCents() {
        for (var i = 0; i < 1000; i++) {
            var unitPrice =
                    prices.calculate(new ProductItem(UUID.randomUUID(), 1)).unitPrice();

            assertThat(BigDecimal.valueOf(unitPrice).scale()).isLessThanOrEqualTo(2);
        }
    }
}
