package io.eventdriven.decider.shoppingcarts;

import io.eventdriven.decider.shoppingcarts.productitems.ProductItems;

import java.time.OffsetDateTime;
import java.util.UUID;

sealed public interface ShoppingCart {
  record EmptyShoppingCart() implements ShoppingCart {
  }

  record PendingShoppingCart(
    UUID id,
    UUID clientId,
    ProductItems productItems
  ) implements ShoppingCart {
  }

  record ConfirmedShoppingCart(
    UUID id,
    UUID clientId,
    ProductItems productItems,
    OffsetDateTime confirmedAt
  ) implements ShoppingCart {
  }

  record CanceledShoppingCart(
    UUID id,
    UUID clientId,
    ProductItems productItems,
    OffsetDateTime canceledAt
  ) implements ShoppingCart {
  }

  static ShoppingCart evolve(
    ShoppingCart state,
    ShoppingCartEvent event
  ) {
    return switch (event) {
      case ShoppingCartEvent.ShoppingCartOpened shoppingCartOpened: {
        if (!(state instanceof EmptyShoppingCart))
          yield state;

        yield new PendingShoppingCart(
          shoppingCartOpened.shoppingCartId(),
          shoppingCartOpened.clientId(),
          ProductItems.empty()
        );
      }
      case ShoppingCartEvent.ProductItemAddedToShoppingCart productItemAddedToShoppingCart: {
        if(!(state instanceof PendingShoppingCart pendingShoppingCart))
          yield state;

        yield new ShoppingCart.PendingShoppingCart(
          pendingShoppingCart.id(),
          pendingShoppingCart.clientId(),
          pendingShoppingCart.productItems().add(productItemAddedToShoppingCart.productItem())
        );
      }
      case ShoppingCartEvent.ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart: {
        if (!(state instanceof PendingShoppingCart pendingShoppingCart))
          yield state;

        yield new ShoppingCart.PendingShoppingCart(
          pendingShoppingCart.id(),
          pendingShoppingCart.clientId(),
          pendingShoppingCart.productItems().remove(productItemRemovedFromShoppingCart.productItem())
        );
      }
      case ShoppingCartEvent.ShoppingCartConfirmed shoppingCartConfirmed: {
        if (!(state instanceof PendingShoppingCart pendingShoppingCart))
          yield state;

        yield new ShoppingCart.ConfirmedShoppingCart(
          pendingShoppingCart.id(),
          pendingShoppingCart.clientId(),
          pendingShoppingCart.productItems(),
          shoppingCartConfirmed.confirmedAt()
        );
      }
      case ShoppingCartEvent.ShoppingCartCanceled shoppingCartCanceled:{
        if (!(state instanceof PendingShoppingCart pendingShoppingCart))
          yield state;

        yield new ShoppingCart.ConfirmedShoppingCart(
          pendingShoppingCart.id(),
          pendingShoppingCart.clientId(),
          pendingShoppingCart.productItems(),
          shoppingCartCanceled.canceledAt()
        );
      }
    };
  }
}

