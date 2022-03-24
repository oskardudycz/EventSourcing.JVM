package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartConfirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartCanceled;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public record ShoppingCart(
  UUID id,
  UUID clientId,
  ShoppingCart.Status status,
  ProductItems productItems,
  Optional<LocalDateTime> confirmedAt,
  Optional<LocalDateTime> canceledAt
) {

  public enum Status {
    Pending,
    Confirmed,
    Cancelled;

    static final EnumSet<Status> Closed = EnumSet.of(Confirmed, Cancelled);
  }

  public static ShoppingCart empty(){
    return new ShoppingCart(null, null, null, null, Optional.empty(), Optional.empty());
  }

  public static String mapToStreamId(UUID shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId);
  }

  public boolean isClosed() {
    return Status.Closed.contains(status);
  }

  public static ShoppingCart when(ShoppingCart current, ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartOpened shoppingCartOpened:
        yield new ShoppingCart(
          shoppingCartOpened.shoppingCartId(),
          shoppingCartOpened.clientId(),
          Status.Pending,
          ProductItems.empty(),
          Optional.empty(),
          Optional.empty()
        );
      case ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().add(productItemAddedToShoppingCart.productItem()),
          Optional.empty(),
          Optional.empty()
        );
      case ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().remove(productItemRemovedFromShoppingCart.productItem()),
          Optional.empty(),
          Optional.empty()
        );
      case ShoppingCartConfirmed shoppingCartConfirmed:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          Status.Confirmed,
          current.productItems(),
          Optional.of(shoppingCartConfirmed.confirmedAt()),
          Optional.empty()
        );
      case ShoppingCartCanceled shoppingCartCanceled:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          Status.Confirmed,
          current.productItems(),
          Optional.empty(),
          Optional.of(shoppingCartCanceled.canceledAt())
        );
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    };
  }
}
