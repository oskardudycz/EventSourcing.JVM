package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.EventHandlerWrapper;
import io.eventdriven.ecommerce.core.events.EventHandler;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
class ShoppingCartDetailsConfig {
  @Bean
  @RequestScope
  public ShoppingCartDetailsProjection shoppingCartDetailsProjectionForDetails(ShoppingCartDetailsRepository repository) {
    return new ShoppingCartDetailsProjection(repository);
  }

  @Bean
  @RequestScope
  public EventHandler<Events.ShoppingCartOpened> handleShoppingCartOpenedForDetails(ShoppingCartDetailsProjection projection) {
    return EventHandlerWrapper.of(
      Events.ShoppingCartOpened.class,
      (event) -> projection.handleShoppingCartOpened(event)
    );
  }

  @Bean
  @RequestScope
  public EventHandler<Events.ProductItemAddedToShoppingCart> handleProductItemAddedToShoppingCartForDetails(ShoppingCartDetailsProjection projection) {
    return EventHandlerWrapper.of(
      Events.ProductItemAddedToShoppingCart.class,
      (event) -> projection.handleProductItemAddedToShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public EventHandler<Events.ProductItemRemovedFromShoppingCart> handleProductItemRemovedFromShoppingCartForDetails(ShoppingCartDetailsProjection projection) {
    return EventHandlerWrapper.of(
      Events.ProductItemRemovedFromShoppingCart.class,
      (event) -> projection.handleProductItemRemovedFromShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public EventHandler<Events.ShoppingCartConfirmed> handleShoppingCartConfirmedForDetails(ShoppingCartDetailsProjection projection) {
    return EventHandlerWrapper.of(
      Events.ShoppingCartConfirmed.class,
      (event) -> projection.handleShoppingCartConfirmed(event)
    );
  }

  @Bean
  @RequestScope
  public EventHandler<Events.ShoppingCartCanceled> handleShoppingCartCanceledForDetails(ShoppingCartDetailsProjection projection) {
    return EventHandlerWrapper.of(
      Events.ShoppingCartCanceled.class,
      (event) -> projection.handleShoppingCartCanceled(event)
    );
  }
}
