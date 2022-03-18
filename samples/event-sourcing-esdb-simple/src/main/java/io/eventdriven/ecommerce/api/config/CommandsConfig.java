package io.eventdriven.ecommerce.api.config;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.commands.CommandHandler;
import io.eventdriven.ecommerce.core.commands.Handle;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

@Configuration
public class CommandsConfig {
  @Bean
  @RequestScope
  public CommandHandler<OpenShoppingCart> handleInitializeShoppingCart(EventStoreDBClient eventStore) {
    return (command) -> Handle.Add(
      eventStore,
      c -> OpenShoppingCart.Handle(c),
      ShoppingCart.mapToStreamId(command.shoppingCartId()),
      command
    );
  }

  @Bean
  @RequestScope
  CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart(EventStoreDBClient eventStore, IProductPriceCalculator productPriceCalculator) {
    return (command) -> GetAndUpdate(
      eventStore,
      (shoppingCart, c) -> AddProductItemToShoppingCart.Handle(productPriceCalculator, c, shoppingCart),
      command.shoppingCartId(),
      command,
      Optional.empty()
    );
  }


  @Bean
  @RequestScope
  CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart(EventStoreDBClient eventStore) {
    return (command) -> GetAndUpdate(
      eventStore,
      (shoppingCart, c) -> RemoveProductItemFromShoppingCart.Handle(c, shoppingCart),
      command.shoppingCartId(),
      command,
      Optional.empty()
    );
  }

  @Bean
  @RequestScope
  CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart(EventStoreDBClient eventStore) {
    return (command) -> GetAndUpdate(
      eventStore,
      (shoppingCart, c) -> ConfirmShoppingCart.Handle(c, shoppingCart),
      command.shoppingCartId(),
      command,
      Optional.empty()
    );
  }

  @Bean
  @RequestScope
  CommandHandler<CancelShoppingCart> handleCancelShoppingCart(EventStoreDBClient eventStore) {
    return (command) -> GetAndUpdate(
      eventStore,
      (shoppingCart, c) -> CancelShoppingCart.Handle(c, shoppingCart),
      command.shoppingCartId(),
      command,
      Optional.empty()
    );
  }

  <TCommand> CompletableFuture<Void> GetAndUpdate(
    EventStoreDBClient eventStore,
    BiFunction<ShoppingCart, TCommand, Object> handle,
    UUID shoppingCartId,
    TCommand command,
    Optional<Long> expectedRevision
  ) throws ExecutionException, InterruptedException {
    return Handle.GetAndUpdate(
      eventStore,
      () -> ShoppingCart.empty(),
      (shoppingCart, event) -> ShoppingCart.when(shoppingCart, (Events.ShoppingCartEvent) event),
      handle,
      ShoppingCart.mapToStreamId(shoppingCartId),
      command,
      expectedRevision
    );
  }

  @Bean
  @ApplicationScope
  IProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }
}
