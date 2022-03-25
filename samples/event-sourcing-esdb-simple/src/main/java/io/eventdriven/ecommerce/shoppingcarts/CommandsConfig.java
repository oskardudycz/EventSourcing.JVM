package io.eventdriven.ecommerce.shoppingcarts;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.commands.CommandHandler;
import io.eventdriven.ecommerce.core.entities.EntityStore;
import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.pricing.RandomProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.addingproductitem.AddProductItemToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.canceling.CancelShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.confirming.ConfirmShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.opening.OpenShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.removingproductitem.RemoveProductItemFromShoppingCart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.ApplicationScope;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
class CommandsConfig {
  @Bean
  @RequestScope
  CommandHandler<OpenShoppingCart> handleInitializeShoppingCart(EntityStore<ShoppingCart, ShoppingCartEvent> store) {
    return command ->
      store.add(
        () -> OpenShoppingCart.handle(command),
        command.shoppingCartId()
      );
  }

  @Bean
  @RequestScope
  CommandHandler<AddProductItemToShoppingCart> handleAddProductItemToShoppingCart(
    EntityStore<ShoppingCart, ShoppingCartEvent> store,
    ProductPriceCalculator productPriceCalculator
  ) {
    return command ->
      store.getAndUpdate(
        current -> AddProductItemToShoppingCart.handle(productPriceCalculator, command, current),
        command.shoppingCartId(),
        command.expectedVersion()
      );
  }


  @Bean
  @RequestScope
  CommandHandler<RemoveProductItemFromShoppingCart> handleRemoveProductItemFromShoppingCart(EntityStore<ShoppingCart, ShoppingCartEvent> store) {
    return command ->
      store.getAndUpdate(
        current -> RemoveProductItemFromShoppingCart.handle(command, current),
        command.shoppingCartId(),
        command.expectedVersion()
      );
  }

  @Bean
  @RequestScope
  CommandHandler<ConfirmShoppingCart> handleConfirmShoppingCart(EntityStore<ShoppingCart, ShoppingCartEvent> store) {
    return command ->
      store.getAndUpdate(
        current -> ConfirmShoppingCart.handle(command, current),
        command.shoppingCartId(),
        command.expectedVersion()
      );
  }

  @Bean
  @RequestScope
  CommandHandler<CancelShoppingCart> handleCancelShoppingCart(EntityStore<ShoppingCart, ShoppingCartEvent> store) {
    return command ->
      store.getAndUpdate(
        current -> CancelShoppingCart.handle(command, current),
        command.shoppingCartId(),
        command.expectedVersion()
      );
  }

  @Bean
  @ApplicationScope
  ProductPriceCalculator productPriceCalculator() {
    return new RandomProductPriceCalculator();
  }

  @Bean
  @ApplicationScope
  EntityStore<ShoppingCart, ShoppingCartEvent> shoppingCartStore(EventStoreDBClient eventStore) {
    return new EntityStore<>(
      eventStore,
      ShoppingCart::when,
      ShoppingCart::mapToStreamId,
      ShoppingCart::empty
    );
  }
}
