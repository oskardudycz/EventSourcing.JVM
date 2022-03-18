package io.eventdriven.ecommerce.shoppingcarts.gettingcartbyid;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.util.ArrayList;

public final class ShoppingCartDetailsProjection {
  public static ShoppingCartDetails HandleShoppingCartOpened(EventEnvelope<Events.ShoppingCartOpened> eventEnvelope) {
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

  public static void HandleProductAdded(EventEnvelope<Events.ProductItemAddedToShoppingCart> eventEnvelope, ShoppingCartDetails view) {
    if (wasAlreadyApplied(eventEnvelope, view))
      return;

    var event = eventEnvelope.data();

    var productItem = event.productItem();
    var existingProductItem = view.getProductItems().stream()
      .filter(x -> x.getProductId() == productItem.productId())
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

    view.setMetadata(eventEnvelope.metadata());
  }

  public static void HandleProductItemRemovedFromShoppingCart(EventEnvelope<Events.ProductItemRemovedFromShoppingCart> eventEnvelope, ShoppingCartDetails view) {
    if (wasAlreadyApplied(eventEnvelope, view))
      return;

    var productItem = eventEnvelope.data().productItem();
    var existingProductItem = view.getProductItems().stream()
      .filter(x -> x.getProductId() == productItem.productId())
      .findFirst();

    if (existingProductItem.isEmpty()) {
      // that's unexpected, but we have to leave with that
      return;
    }

    if (existingProductItem.get().getQuantity() == productItem.quantity()) {
      view.getProductItems().remove(existingProductItem);
    } else {
      existingProductItem.get().decreaseQuantity(productItem.quantity());
    }

    view.setMetadata(eventEnvelope.metadata());
  }

  public static void HandleShoppingCartConfirmed(EventEnvelope<Events.ShoppingCartConfirmed> eventEnvelope, ShoppingCartDetails view) {
    if (wasAlreadyApplied(eventEnvelope, view))
      return;

    view.setStatus(ShoppingCart.Status.Confirmed);

    view.setMetadata(eventEnvelope.metadata());
  }

  public static void HandleShoppingCartCanceled(EventEnvelope<Events.ShoppingCartCanceled> eventEnvelope, ShoppingCartDetails view) {
    if (wasAlreadyApplied(eventEnvelope, view))
      return;

    view.setStatus(ShoppingCart.Status.Confirmed);

    view.setMetadata(eventEnvelope.metadata());
  }

  private static boolean wasAlreadyApplied(EventEnvelope<?> eventEnvelope, ShoppingCartDetails view) {
    return view.getLastProcessedPosition() >= eventEnvelope.metadata().logPosition();
  }
}
