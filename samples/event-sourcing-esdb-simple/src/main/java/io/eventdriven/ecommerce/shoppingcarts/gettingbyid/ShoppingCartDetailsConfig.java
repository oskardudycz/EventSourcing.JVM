package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.IEventHandler;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class ShoppingCartDetailsConfig {
  @Bean
  @Scope
  public IEventHandler<Events.ShoppingCartOpened> handleShoppingCartOpened(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Add(Events.ShoppingCartOpened.class, repository, event -> ShoppingCartDetailsProjection.handleShoppingCartOpened(event));
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemAddedToShoppingCart> handleProductItemAddedToShoppingCart(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Update(
      Events.ProductItemAddedToShoppingCart.class,
      repository,
      event -> event.shoppingCartId(),
      (view, event) -> ShoppingCartDetailsProjection.handleProductItemAddedToShoppingCart(view, event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemRemovedFromShoppingCart> handleProductItemRemovedFromShoppingCart(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Update(
      Events.ProductItemRemovedFromShoppingCart.class,
      repository,
      event -> event.shoppingCartId(),
      (view, event) -> ShoppingCartDetailsProjection.handleProductItemRemovedFromShoppingCart(view, event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartConfirmed> handleShoppingCartConfirmed(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Update(
      Events.ShoppingCartConfirmed.class,
      repository,
      event -> event.shoppingCartId(),
      (view, event) -> ShoppingCartDetailsProjection.handleShoppingCartConfirmed(view, event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartCanceled> handleShoppingCartCanceled(ShoppingCartDetailsRepository repository) {
    return JPAProjection.Update(
      Events.ShoppingCartCanceled.class,
      repository,
      event -> event.shoppingCartId(),
      (view, event) -> ShoppingCartDetailsProjection.handleShoppingCartCanceled(view, event)
    );
  }
}
