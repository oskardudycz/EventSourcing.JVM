package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.EventHandler;
import io.eventdriven.ecommerce.core.events.IEventHandler;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class ShoppingCartDetailsConfig {
  @Bean
  @RequestScope
  public ShoppingCartDetailsProjection shoppingCartDetailsProjection(ShoppingCartDetailsRepository repository) {
    return new ShoppingCartDetailsProjection(repository);
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartOpened> handleShoppingCartOpened(ShoppingCartDetailsProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartOpened.class,
      (event) -> projection.handleShoppingCartOpened(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemAddedToShoppingCart> handleProductItemAddedToShoppingCart(ShoppingCartDetailsProjection projection) {
    return EventHandler.of(
      Events.ProductItemAddedToShoppingCart.class,
      (event) -> projection.handleProductItemAddedToShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemRemovedFromShoppingCart> handleProductItemRemovedFromShoppingCart(ShoppingCartDetailsProjection projection) {
    return EventHandler.of(
      Events.ProductItemRemovedFromShoppingCart.class,
      (event) -> projection.handleProductItemRemovedFromShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartConfirmed> handleShoppingCartConfirmed(ShoppingCartDetailsProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartConfirmed.class,
      (event) -> projection.handleShoppingCartConfirmed(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartCanceled> handleShoppingCartCanceled(ShoppingCartDetailsProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartCanceled.class,
      (event) -> projection.handleShoppingCartCanceled(event)
    );
  }
}
