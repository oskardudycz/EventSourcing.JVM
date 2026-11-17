package io.eventdriven.distributedprocesses.ecommerce.api;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.ThrowingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class TestConfigurations {
  private TestConfigurations() {
  }

  static class Prices implements ProductPriceCalculator {
    private final Map<UUID, Double> prices = new ConcurrentHashMap<>();

    void set(UUID productId, double unitPrice) {
      prices.put(productId, unitPrice);
    }

    @Override
    public PricedProductItem calculate(ProductItem productItem) {
      return new PricedProductItem(productItem, prices.getOrDefault(productItem.productId(), 10.0));
    }
  }

  static class Clock implements Supplier<OffsetDateTime> {
    private final AtomicReference<OffsetDateTime> now = new AtomicReference<>(OffsetDateTime.now());

    void moveBy(Duration duration) {
      now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public OffsetDateTime get() {
      return now.get();
    }
  }

  @TestConfiguration
  static class ChangeablePrices {
    @Bean
    @Primary
    Prices prices() {
      return new Prices();
    }
  }

  @TestConfiguration
  static class MovableClock {
    @Bean
    @Primary
    Clock movableClock() {
      return new Clock();
    }
  }

  @TestConfiguration
  static class UnreachableGateway {
    @Bean
    @Primary
    PaymentGateway unreachableGateway() {
      return new ThrowingPaymentGateway();
    }
  }
}
