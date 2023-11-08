package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;

import java.util.UUID;

sealed public interface ShoppingCart {
  record Empty() implements ShoppingCart {
  }

  record Pending(
    ProductItems productItems
  ) implements ShoppingCart {
  }

  record Closed() implements ShoppingCart {
  }

  static ShoppingCart evolve(ShoppingCart state, ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartOpened ignore: {
        if (!(state instanceof Empty))
          yield state;

        yield new Pending(ProductItems.empty());
      }
      case ProductItemAddedToShoppingCart(var ignore, var productItem): {
        if (!(state instanceof Pending pending))
          yield state;

        yield new Pending(
          pending.productItems().with(productItem)
        );
      }
      case ProductItemRemovedFromShoppingCart(var ignore, var productItem): {
        if (!(state instanceof Pending pending))
          yield state;

        yield new Pending(
          pending.productItems().without(productItem)
        );
      }
      case ShoppingCartConfirmed ignore: {
        if (!(state instanceof Pending))
          yield state;

        yield new Closed();
      }
      case ShoppingCartCanceled ignore: {
        if (!(state instanceof Pending))
          yield state;

        yield new Closed();
      }
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    };
  }

  static ShoppingCart empty() {
    return new Empty();
  }

  static String mapToStreamId(UUID shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId);
  }
}
