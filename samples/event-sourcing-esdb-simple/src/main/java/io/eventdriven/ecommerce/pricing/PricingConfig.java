package io.eventdriven.ecommerce.pricing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class PricingConfig {
  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }
}

