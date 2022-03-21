package io.eventdriven.ecommerce.shoppingcarts.config;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.commands.CommandHandler;
import io.eventdriven.ecommerce.core.entities.EntityStore;
import io.eventdriven.ecommerce.pricing.IProductPriceCalculator;
import io.eventdriven.ecommerce.pricing.RandomProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.addingproductitem.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.canceling.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.confirming.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.opening.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.removingproductitem.RemoveProductItemFromShoppingCart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Configuration
public class CommandsConfig {
  @Bean
  @RequestScope
  public CommandHandler<OpenShoppingCart> handleInitializeShoppingCart(EntityStore<ShoppingCart> store) {
    return command ->
      store.Add(
        () -> OpenShoppingCart.Handle(command),
        command.shoppingCartId()
      );
  }

  @Bean
  @RequestScope
  CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart(
    EntityStore<ShoppingCart> store,
    IProductPriceCalculator productPriceCalculator
  ) {
    return command ->
      store.GetAndUpdate(
        current -> AddProductItemToShoppingCart.Handle(productPriceCalculator, command, current),
        command.shoppingCartId(),
        Optional.of(command.expectedVersion())
      );
  }


  @Bean
  @RequestScope
  CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart(EntityStore<ShoppingCart> store) {
    return command ->
      store.GetAndUpdate(
        current -> RemoveProductItemFromShoppingCart.Handle(command, current),
        command.shoppingCartId(),
        Optional.of(command.expectedVersion())
      );
  }

  @Bean
  @RequestScope
  CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart(EntityStore<ShoppingCart> store) {
    return command ->
      store.GetAndUpdate(
        current -> ConfirmShoppingCart.Handle(command, current),
        command.shoppingCartId(),
        Optional.of(command.expectedVersion())
      );
  }

  @Bean
  @RequestScope
  CommandHandler<CancelShoppingCart> handleCancelShoppingCart(EntityStore<ShoppingCart> store) {
    return command ->
      store.GetAndUpdate(
        current -> CancelShoppingCart.Handle(command, current),
        command.shoppingCartId(),
        Optional.of(command.expectedVersion())
      );
  }

  @Bean
  @ApplicationScope
  IProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }

  @Bean
  @ApplicationScope
  EntityStore<ShoppingCart> shoppingCartStore(EventStoreDBClient eventStore) {
    return new EntityStore<>(
      eventStore,
      (state, event) -> ShoppingCart.when(state, (Events.ShoppingCartEvent) event),
      ShoppingCart::mapToStreamId,
      ShoppingCart::empty
    );
  }
}