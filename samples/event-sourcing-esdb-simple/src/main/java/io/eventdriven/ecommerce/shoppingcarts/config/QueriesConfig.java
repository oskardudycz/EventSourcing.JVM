package io.eventdriven.ecommerce.shoppingcarts.config;

import io.eventdriven.ecommerce.core.queries.QueryHandler;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.GetShoppingCartById;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Configuration
public class QueriesConfig {
  @Bean
  @RequestScope
  public QueryHandler<GetShoppingCartById, Optional<ShoppingCartDetails>> handleGetById(ShoppingCartDetailsRepository repository) {
    return query -> GetShoppingCartById.Handle(repository, query);
  }
}
