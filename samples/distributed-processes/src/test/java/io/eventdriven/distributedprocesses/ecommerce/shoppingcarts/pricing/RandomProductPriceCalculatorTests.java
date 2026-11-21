package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    @Test
    public void concurrentRequestsForOneNewProductGetOnePrice() throws Exception {
        var threads = 50;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var round = 0; round < 20; round++) {
                var productItem = new ProductItem(UUID.randomUUID(), 1);
                var start = new CountDownLatch(1);
                var results = new ArrayList<Future<Double>>();
                for (var i = 0; i < threads; i++)
                    results.add(executor.submit(() -> {
                        start.await();
                        return prices.calculate(productItem).unitPrice();
                    }));

                start.countDown();

                var unitPrices = new ArrayList<Double>();
                for (var result : results) unitPrices.add(result.get());
                assertThat(unitPrices).containsOnly(unitPrices.getFirst());
            }
        }
    }
}
