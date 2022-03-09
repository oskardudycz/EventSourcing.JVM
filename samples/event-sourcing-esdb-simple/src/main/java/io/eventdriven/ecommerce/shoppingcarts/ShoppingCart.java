package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItemsList;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public record ShoppingCart(
  UUID id,
  UUID clientId,
  ShoppingCart.Status status,
  ProductItemsList productItems,
  Optional<LocalDateTime> confirmedAt
) {

  public enum Status {
    Pending,
    Confirmed,
    Cancelled;

    public static final EnumSet<Status> Closed = EnumSet.of(Confirmed, Cancelled);
  }

  public static ShoppingCart empty(){
    return new ShoppingCart(null, null, null, null, Optional.empty());
  }

  public static String mapToStreamId(UUID shoppingCartId) {
    return "ShoppingCart-%s".formatted(shoppingCartId);
  }

  public boolean isClosed() {
    return Status.Closed.contains(status);
  }

  public static ShoppingCart when(ShoppingCart current, Events.ShoppingCartEvent event) {
    return switch (event) {
      case Events.ShoppingCartInitialized shoppingCartInitialized:
        yield new ShoppingCart(
          shoppingCartInitialized.shoppingCartId(),
          shoppingCartInitialized.clientId(),
          Status.Pending,
          ProductItemsList.empty(),
          Optional.empty()
        );
      case Events.ProductItemAddedToShoppingCart productItemAddedToShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().add(productItemAddedToShoppingCart.productItem()),
          current.confirmedAt()
        );
      case Events.ProductItemRemovedFromShoppingCart productItemRemovedFromShoppingCart:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          current.status,
          current.productItems().remove(productItemRemovedFromShoppingCart.productItem()),
          current.confirmedAt()
        );
      case Events.ShoppingCartConfirmed shoppingCartConfirmed:
        yield new ShoppingCart(
          current.id(),
          current.clientId,
          Status.Confirmed,
          current.productItems(),
          Optional.of(shoppingCartConfirmed.confirmedAt())
        );
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    };
  }
}
