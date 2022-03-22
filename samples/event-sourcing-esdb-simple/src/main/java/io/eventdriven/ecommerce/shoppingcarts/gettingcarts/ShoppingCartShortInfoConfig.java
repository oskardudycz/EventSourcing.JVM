package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import io.eventdriven.ecommerce.core.events.EventHandler;
import io.eventdriven.ecommerce.core.events.IEventHandler;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class ShoppingCartShortInfoConfig {
  @Bean
  @RequestScope
  public ShoppingCartShortInfoProjection shoppingCartShortInfoProjection(ShoppingCartShortInfoRepository repository) {
    return new ShoppingCartShortInfoProjection(repository);
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartOpened> handleShoppingCartOpenedForShortInfo(ShoppingCartShortInfoProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartOpened.class,
      (event) -> projection.handleShoppingCartOpened(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemAddedToShoppingCart> handleProductItemAddedToShoppingCartForShortInfo(ShoppingCartShortInfoProjection projection) {
    return EventHandler.of(
      Events.ProductItemAddedToShoppingCart.class,
      (event) -> projection.handleProductItemAddedToShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ProductItemRemovedFromShoppingCart> handleProductItemRemovedFromShoppingCartForShortInfo(ShoppingCartShortInfoProjection projection) {
    return EventHandler.of(
      Events.ProductItemRemovedFromShoppingCart.class,
      (event) -> projection.handleProductItemRemovedFromShoppingCart(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartConfirmed> handleShoppingCartConfirmedForShortInfo(ShoppingCartShortInfoProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartConfirmed.class,
      (event) -> projection.handleShoppingCartConfirmed(event)
    );
  }

  @Bean
  @RequestScope
  public IEventHandler<Events.ShoppingCartCanceled> handleShoppingCartCanceledForShortInfo(ShoppingCartShortInfoProjection projection) {
    return EventHandler.of(
      Events.ShoppingCartCanceled.class,
      (event) -> projection.handleShoppingCartCanceled(event)
    );
  }
}
