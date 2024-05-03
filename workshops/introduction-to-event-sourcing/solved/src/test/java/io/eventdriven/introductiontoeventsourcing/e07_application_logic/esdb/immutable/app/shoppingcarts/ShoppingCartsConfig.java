package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.entities.CommandHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.pricing.ProductPriceCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class ShoppingCartsConfig {
  @Bean
  ShoppingCartDecider shoppingCartDecider(
    ProductPriceCalculator productPriceCalculator
  ) {
    return new ShoppingCartDecider(
      productPriceCalculator
    );
  }

  @Bean
  @ApplicationScope
  CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> shoppingCartStore(
    EventStoreDBClient eventStore,
    ShoppingCartDecider decider
  ) {
    return new CommandHandler<>(
      eventStore,
      ShoppingCart::when,
      decider::handle,
      ShoppingCart::mapToStreamId,
      ShoppingCart::empty
    );
  }
}

