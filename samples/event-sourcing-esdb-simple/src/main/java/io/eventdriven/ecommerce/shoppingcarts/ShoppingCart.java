package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;

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

  public static ShoppingCart when(ShoppingCart current, Events.ShoppingCartEvent event) {
    return switch (event) {
      case Events.ShoppingCartOpened shoppingCartOpened:
        yield new ShoppingCart(
          shoppingCartOpened.shoppingCartId(),
          shoppingCartOpened.clientId(),
          Status.Pending,
          ProductItems.empty(),
          Optional.empty(),
          Optional.empty()
        );
      case Events.ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().add(productItemAddedToShoppingCart.productItem()),
          Optional.empty(),
          Optional.empty()
        );
      case Events.ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().remove(productItemRemovedFromShoppingCart.productItem()),
          Optional.empty(),
          Optional.empty()
        );
      case Events.ShoppingCartConfirmed shoppingCartConfirmed:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          Status.Confirmed,
          current.productItems(),
          Optional.of(shoppingCartConfirmed.confirmedAt()),
          Optional.empty()
        );
      case Events.ShoppingCartCanceled shoppingCartCanceled:
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
