package io.eventdriven.ecommerce.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.entities.EntityStore;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class ShoppingCartsConfig {
  @Bean
  ShoppingCartService shoppingCartService(
    EntityStore<ShoppingCart, ShoppingCartEvent> entityStore,
    ShoppingCartDetailsRepository detailsRepository,
    ShoppingCartShortInfoRepository shortInfoRepository,
    ProductPriceCalculator productPriceCalculator
  ) {
    return new ShoppingCartService(
      entityStore,
      detailsRepository,
      shortInfoRepository,
      productPriceCalculator
    );
  }

  @Bean
  @ApplicationScope
  EntityStore<ShoppingCart, ShoppingCartEvent> shoppingCartStore(EventStoreDBClient eventStore) {
    return new EntityStore<>(
      eventStore,
      ShoppingCart::when,
      ShoppingCart::mapToStreamId,
      ShoppingCart::empty
    );
  }
}

