package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;

import java.time.OffsetDateTime;
import java.util.UUID;

sealed public interface ShoppingCart {
  record Initial() implements ShoppingCart {
  }

  record Pending(
    UUID id,
    UUID clientId,
    ProductItems productItems
  ) implements ShoppingCart {
  }

  record Confirmed(
    UUID id,
    UUID clientId,
    ProductItems productItems,
    OffsetDateTime confirmedAt
  ) implements ShoppingCart {
  }

  record Canceled(
    UUID id,
    UUID clientId,
    ProductItems productItems,
    OffsetDateTime canceledAt
  ) implements ShoppingCart {
  }

  default boolean isClosed() {
    return this instanceof Confirmed || this instanceof Canceled;
  }

  static ShoppingCart when(ShoppingCart current, ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartOpened shoppingCartOpened: {
        if (!(current instanceof Initial))
          yield current;

        yield new Pending(
          shoppingCartOpened.shoppingCartId(),
          shoppingCartOpened.clientId(),
          ProductItems.empty()
        );
      }
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart: {
        if (!(current instanceof Pending pending))
          yield current;

        yield new Pending(
          pending.id(),
          pending.clientId(),
          pending.productItems().add(productItemAddedToShoppingCart.productItem())
        );
      }
      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart: {
        if (!(current instanceof Pending pending))
          yield current;

        yield new Pending(
          pending.id(),
          pending.clientId(),
          pending.productItems().remove(productItemRemovedFromShoppingCart.productItem())
        );
      }
      case ShoppingCartConfirmed shoppingCartConfirmed: {
        if (!(current instanceof Pending pending))
          yield current;

        yield new Confirmed(
          pending.id(),
          pending.clientId(),
          pending.productItems(),
          shoppingCartConfirmed.confirmedAt()
        );
      }
      case ShoppingCartCanceled shoppingCartCanceled: {
        if (!(current instanceof Pending pending))
          yield current;

        yield new Canceled(
          pending.id(),
          pending.clientId(),
          pending.productItems(),
          shoppingCartCanceled.canceledAt()
        );
      }
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    };
  }

  static ShoppingCart empty() {
    return new Initial();
  }

  static String mapToStreamId(UUID shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId);
  }
}
