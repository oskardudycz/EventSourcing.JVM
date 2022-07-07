package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PricingConfig {
  @Bean
  ProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }
}

