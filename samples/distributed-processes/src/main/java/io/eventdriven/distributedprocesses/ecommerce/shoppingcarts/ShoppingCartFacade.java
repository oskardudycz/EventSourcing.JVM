package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

public class ShoppingCartFacade {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store;
  private final ProductPriceCalculator productPriceCalculator;
  private final Supplier<OffsetDateTime> now;

  static String mapToStreamId(ShoppingCartId shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId.tail());
  }

  public ShoppingCartFacade(
    AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store,
    ProductPriceCalculator productPriceCalculator,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.productPriceCalculator = productPriceCalculator;
    this.now = now;
  }

  public void open(OpenShoppingCart command) {
    store.getAndUpdate(
      command.shoppingCartId(),
      current -> current.open(command.shoppingCartId(), command.clientId())
    );
  }

  public void addProductItem(AddProductItemToShoppingCart command) {
    store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.addProductItem(productPriceCalculator, command.productItem())
    );
  }

  public void removeProductItem(RemoveProductItemFromShoppingCart command) {
    store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.removeProductItem(command.productItem())
    );
  }

  public void confirm(ConfirmShoppingCart command) {
    store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.confirm(now.get())
    );
  }

  public void cancel(CancelShoppingCart command) {
    store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.cancel(now.get())
    );
  }
}
