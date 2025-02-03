package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts.productItems.ProductItems;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.immutable.app.shoppingcarts.ShoppingCartEvent.*;

public record ShoppingCart(
  UUID id,
  UUID clientId,
  Status status,
  ProductItems productItems,
  OffsetDateTime confirmedAt,
  OffsetDateTime canceledAt
) {
  public enum Status {
    Pending,
    Confirmed,
    Canceled
  }

  public boolean isClosed() {
    return status == Status.Confirmed || status == Status.Canceled;
  }

  public static ShoppingCart initial() {
    return new ShoppingCart(null, null, null, null, null, null);
  }

  public static ShoppingCart evolve(ShoppingCart state, ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartOpened opened -> new ShoppingCart(
        opened.shoppingCartId(),
        opened.clientId(),
        Status.Pending,
        ProductItems.empty(),
        null,
        null
      );
      case ProductItemAddedToShoppingCart(var cartId, var productItem) ->
        new ShoppingCart(
          state.id,
          state.clientId,
          state.status,
          state.productItems.add(productItem),
          state.confirmedAt,
          state.canceledAt
        );
      case ProductItemRemovedFromShoppingCart(var cartId, var productItem) ->
        new ShoppingCart(
          state.id,
          state.clientId,
          state.status,
          state.productItems.remove(productItem),
          state.confirmedAt,
          state.canceledAt
        );
      case ShoppingCartConfirmed confirmed ->
        new ShoppingCart(
          state.id,
          state.clientId,
          Status.Confirmed,
          state.productItems,
          state.confirmedAt,
          state.canceledAt
        );
      case ShoppingCartCanceled canceled ->
        new ShoppingCart(
          state.id,
          state.clientId,
          Status.Canceled,
          state.productItems,
          state.confirmedAt,
          state.canceledAt
        );
    };
  }
}
//
//static ShoppingCart evolve(EventStoreDBClient eventStore, String streamName) {
//  // 1. Add logic here
//  ShoppingCart shoppingCart = null;
//
//
//  for (var event : getEvents(eventStore, streamName)) {
//    switch (event) {
//      case ShoppingCartOpened opened -> shoppingCart = new ShoppingCart(
//        opened.shoppingCartId(),
//        opened.clientId(),
//        ShoppingCartStatus.Pending,
//        new PricedProductItem[]{},
//        null,
//        null
//      );
//      case ProductItemAddedToShoppingCart productItemAdded ->
//        shoppingCart = new ShoppingCart(
//          shoppingCart.id(),
//          shoppingCart.clientId(),
//          shoppingCart.status(),
//          Stream.concat(Arrays.stream(shoppingCart.productItems()), Stream.of(productItemAdded.productItem()))
//            .collect(groupingByOrdered(PricedProductItem::productId))
//            .entrySet().stream()
//            .map(group -> group.getValue().size() == 1 ?
//              group.getValue().get(0) :
//              new PricedProductItem(
//                group.getKey(),
//                group.getValue().stream().mapToInt(PricedProductItem::quantity).sum(),
//                group.getValue().get(0).unitPrice()
//              )
//            )
//            .toArray(PricedProductItem[]::new),
//          shoppingCart.confirmedAt(),
//          shoppingCart.canceledAt()
//        );
//      case ProductItemRemovedFromShoppingCart productItemRemoved ->
//        shoppingCart = new ShoppingCart(
//          shoppingCart.id(),
//          shoppingCart.clientId(),
//          shoppingCart.status(),
//          Arrays.stream(shoppingCart.productItems())
//            .map(pi -> pi.productId().equals(productItemRemoved.productItem().productId()) ?
//              new PricedProductItem(
//                pi.productId(),
//                pi.quantity() - productItemRemoved.productItem().quantity(),
//                pi.unitPrice()
//              )
//              : pi
//            )
//            .filter(pi -> pi.quantity > 0)
//            .toArray(PricedProductItem[]::new),
//          shoppingCart.confirmedAt(),
//          shoppingCart.canceledAt()
//        );
//      case ShoppingCartConfirmed confirmed -> shoppingCart = new ShoppingCart(
//        shoppingCart.id(),
//        shoppingCart.clientId(),
//        ShoppingCartStatus.Confirmed,
//        shoppingCart.productItems(),
//        confirmed.confirmedAt(),
//        shoppingCart.canceledAt()
//      );
//      case ShoppingCartCanceled canceled -> shoppingCart = new ShoppingCart(
//        shoppingCart.id(),
//        shoppingCart.clientId(),
//        ShoppingCartStatus.Canceled,
//        shoppingCart.productItems(),
//        shoppingCart.confirmedAt(),
//        canceled.canceledAt()
//      );
//    }
//  }
//
//  return shoppingCart;
//}
