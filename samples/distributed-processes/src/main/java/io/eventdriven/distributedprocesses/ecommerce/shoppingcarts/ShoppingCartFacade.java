package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

public class ShoppingCartFacade {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store;
  private final CustomerShoppingCarts customerShoppingCarts;
  private final ProductPriceCalculator productPriceCalculator;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(ShoppingCartId shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId.tail());
  }

  public ShoppingCartFacade(
    AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId> store,
    CustomerShoppingCarts customerShoppingCarts,
    ProductPriceCalculator productPriceCalculator,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.customerShoppingCarts = customerShoppingCarts;
    this.productPriceCalculator = productPriceCalculator;
    this.now = now;
  }

  public ETag addProductItem(AddProductItemToShoppingCart command) {
    var customerId = command.customerId();
    var shoppingCartId = customerShoppingCarts.currentOf(customerId)
      .orElseGet(() -> ShoppingCartId.of(customerId, customerShoppingCarts.countOf(customerId) + 1));

    return store.getAndUpdate(
      shoppingCartId,
      current -> current.addProductItem(shoppingCartId, customerId, productPriceCalculator, command.productItem(), now.get())
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
