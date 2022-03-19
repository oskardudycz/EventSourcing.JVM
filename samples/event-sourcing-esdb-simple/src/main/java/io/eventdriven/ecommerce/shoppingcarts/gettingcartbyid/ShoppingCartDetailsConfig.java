package io.eventdriven.ecommerce.shoppingcarts.gettingcartbyid;

import io.eventdriven.ecommerce.core.events.IEventHandler;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ShoppingCartDetailsConfig {
  @Bean
  @Scope
  public IEventHandler<Events.ShoppingCartOpened> handleShoppingCartOpened(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Add(Events.ShoppingCartOpened.class, repository, event -> ShoppingCartDetailsProjection.handleShoppingCartOpened(event));
  }

  @Bean
  @Scope
  public IEventHandler<Events.ProductItemAddedToShoppingCart> handleProductItemAddedToShoppingCart(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Update(
      Events.ProductItemAddedToShoppingCart.class,
      repository,
      event -> event.shoppingCartId(),
      (view, event) -> ShoppingCartDetailsProjection.handleProductItemAddedToShoppingCart(view, event)
    );
  }
}
