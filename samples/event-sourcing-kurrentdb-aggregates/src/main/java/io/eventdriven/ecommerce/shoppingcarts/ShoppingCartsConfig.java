package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.core.aggregates.AggregateStore;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import io.kurrent.dbclient.KurrentDBClient;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration
class ShoppingCartsConfig {
    @Bean
    ShoppingCartService shoppingCartService(
            AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> entityStore,
            ShoppingCartDetailsRepository detailsRepository,
            ShoppingCartShortInfoRepository shortInfoRepository,
            ProductPriceCalculator productPriceCalculator) {
        return new ShoppingCartService(entityStore, detailsRepository, shortInfoRepository, productPriceCalculator);
    }

    @Bean
    @ApplicationScope
    AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> shoppingCartStore(KurrentDBClient eventStore) {
        return new AggregateStore<>(eventStore, ShoppingCart::mapToStreamId, ShoppingCart::empty);
    }
}
