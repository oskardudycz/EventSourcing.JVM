package io.eventdriven.ecommerce.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.entities.CommandHandler;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class ShoppingCartsConfig {

  @Bean
  @ApplicationScope
  CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> shoppingCartStore(
    EventStoreDBClient eventStore
  ) {
    return new CommandHandler<>(
      eventStore,
      ShoppingCart::evolve,
      ShoppingCartDecider::handle,
      ShoppingCart::mapToStreamId,
      ShoppingCart::empty
    );
  }
}

