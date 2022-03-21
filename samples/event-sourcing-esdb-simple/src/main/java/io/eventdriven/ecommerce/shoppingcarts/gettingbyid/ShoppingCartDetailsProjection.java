package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.util.ArrayList;

public final class ShoppingCartDetailsProjection {
  public static ShoppingCartDetails handleShoppingCartOpened(EventEnvelope<Events.ShoppingCartOpened> eventEnvelope) {
    var event = eventEnvelope.data();

    return new ShoppingCartDetails(
      event.shoppingCartId(),
      event.clientId(),
      ShoppingCart.Status.Pending,
      new ArrayList<>(),
      eventEnvelope.metadata().streamPosition(),
      eventEnvelope.metadata().logPosition()
    );
  }

  public static ShoppingCartDetails handleProductItemAddedToShoppingCart(ShoppingCartDetails view, EventEnvelope<Events.ProductItemAddedToShoppingCart> eventEnvelope) {
    var event = eventEnvelope.data();

    var productItem = event.productItem();
    var existingProductItem = view.getProductItems().stream()
      .filter(x -> x.getProductId().equals(productItem.productId()))
      .findFirst();

    if (existingProductItem.isEmpty()) {
      view.getProductItems().add(
        new ShoppingCartDetailsProductItem(
          productItem.productId(),
          productItem.quantity(),
          productItem.unitPrice()
        )
      );
    } else {
      existingProductItem.get().increaseQuantity(productItem.quantity());
    }

    return view;
  }

  public static ShoppingCartDetails handleProductItemRemovedFromShoppingCart(ShoppingCartDetails view, EventEnvelope<Events.ProductItemRemovedFromShoppingCart> eventEnvelope) {
    var productItem = eventEnvelope.data().productItem();
    var existingProductItem = view.getProductItems().stream()
      .filter(x -> x.getProductId().equals(productItem.productId()))
      .findFirst();

    if (existingProductItem.isEmpty()) {
      // that's unexpected, but we have to leave with that
      return view;
    }

    if (existingProductItem.get().getQuantity() == productItem.quantity()) {
      view.getProductItems().remove(existingProductItem);
    } else {
      existingProductItem.get().decreaseQuantity(productItem.quantity());
    }

    return view;
  }

  public static ShoppingCartDetails handleShoppingCartConfirmed(ShoppingCartDetails view, EventEnvelope<Events.ShoppingCartConfirmed> eventEnvelope) {
    view.setStatus(ShoppingCart.Status.Confirmed);
    return view;
  }

  public static ShoppingCartDetails handleShoppingCartCanceled(ShoppingCartDetails view, EventEnvelope<Events.ShoppingCartCanceled> eventEnvelope) {
    view.setStatus(ShoppingCart.Status.Confirmed);

    return view;
  }

  private static boolean wasAlreadyApplied(EventEnvelope<?> eventEnvelope, ShoppingCartDetails view) {
    return view.getLastProcessedPosition() >= eventEnvelope.metadata().logPosition();
  }
}
