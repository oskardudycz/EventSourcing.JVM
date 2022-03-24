package io.eventdriven.ecommerce.shoppingcarts.config;

import io.eventdriven.ecommerce.core.queries.QueryHandler;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.GetShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfo;
import io.eventdriven.ecommerce.shoppingcarts.gettingcarts.ShoppingCartShortInfoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Configuration
class QueriesConfig {
  @Bean
  @RequestScope
  QueryHandler<GetShoppingCartById, Optional<ShoppingCartDetails>> handleGetById(ShoppingCartDetailsRepository repository) {
    return query -> GetShoppingCartById.handle(repository, query);
  }

  @Bean
  @RequestScope
  QueryHandler<GetShoppingCarts, Page<ShoppingCartShortInfo>> handleGetShoppingCarts(ShoppingCartShortInfoRepository repository) {
    return query -> GetShoppingCarts.handle(repository, query);
  }
}
