package io.eventdriven.ecommerce.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.entities.CommandHandler;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
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

