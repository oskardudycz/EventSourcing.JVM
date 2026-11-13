package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

public class ShoppingCartFacade {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store;
  private final ClientShoppingCarts clientShoppingCarts;
  private final ProductPriceCalculator productPriceCalculator;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(ShoppingCartId shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId.tail());
  }

  public ShoppingCartFacade(
    AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store,
    ClientShoppingCarts clientShoppingCarts,
    ProductPriceCalculator productPriceCalculator,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.clientShoppingCarts = clientShoppingCarts;
    this.productPriceCalculator = productPriceCalculator;
    this.now = now;
  }

  public ETag addProductItem(AddProductItemToShoppingCart command) {
    var clientId = command.clientId();
    var shoppingCartId = clientShoppingCarts.currentOf(clientId)
      .orElseGet(() -> ShoppingCartId.of(clientId, clientShoppingCarts.countOf(clientId) + 1));

    return store.getAndUpdate(
      shoppingCartId,
      current -> current.addProductItem(shoppingCartId, clientId, productPriceCalculator, command.productItem(), now.get())
    );
  }

  public ETag removeProductItem(RemoveProductItemFromShoppingCart command) {
    return store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.removeProductItem(command.productItem(), now.get())
    );
  }

  public ETag checkOut(CheckOutShoppingCart command) {
    return store.getAndUpdate(
      command.shoppingCartId(),
      command.expectedVersion(),
      current -> current.checkOut(productPriceCalculator, now.get())
    );
  }
}
