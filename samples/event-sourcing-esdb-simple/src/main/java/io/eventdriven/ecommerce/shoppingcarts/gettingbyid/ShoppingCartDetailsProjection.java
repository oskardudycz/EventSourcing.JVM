package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.util.ArrayList;
import java.util.UUID;

public class ShoppingCartDetailsProjection extends JPAProjection<ShoppingCartDetails, UUID> {
  protected ShoppingCartDetailsProjection(ShoppingCartDetailsRepository repository) {
    super(repository);
  }

  public void handleShoppingCartOpened(EventEnvelope<Events.ShoppingCartOpened> eventEnvelope) {
    add(eventEnvelope, () -> {
      var event = eventEnvelope.data();

      return new ShoppingCartDetails(
        event.shoppingCartId(),
        event.clientId(),
        ShoppingCart.Status.Pending,
        new ArrayList<>(),
        eventEnvelope.metadata().streamPosition(),
        eventEnvelope.metadata().logPosition()
      );
    });
  }

  public void handleProductItemAddedToShoppingCart(EventEnvelope<Events.ProductItemAddedToShoppingCart> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope, view -> {
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
    });
  }

  public void handleProductItemRemovedFromShoppingCart(EventEnvelope<Events.ProductItemRemovedFromShoppingCart> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope, view -> {
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
    });
  }

  public void handleShoppingCartConfirmed(EventEnvelope<Events.ShoppingCartConfirmed> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.setStatus(ShoppingCart.Status.Confirmed)
    );
  }

  public void handleShoppingCartCanceled(EventEnvelope<Events.ShoppingCartCanceled> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.setStatus(ShoppingCart.Status.Cancelled)
    );
  }
}
