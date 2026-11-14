package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

@Component
class ShoppingCartDetailsProjection extends JPAProjection<ShoppingCartDetails, UUID> {
  protected ShoppingCartDetailsProjection(ShoppingCartDetailsRepository repository) {
    super(repository);
  }

  @EventListener
  void handleShoppingCartOpened(EventEnvelope<ShoppingCartOpened> eventEnvelope) {
    add(eventEnvelope, () -> {
      var event = eventEnvelope.data();

      return new ShoppingCartDetails(
        event.shoppingCartId(),
        event.clientId(),
        ShoppingCartStatus.Pending,
        new ArrayList<>(),
        eventEnvelope.metadata().streamPosition(),
        eventEnvelope.metadata().logPosition()
      );
    });
  }

  @EventListener
  void handleProductItemAddedToShoppingCart(EventEnvelope<ProductItemAddedToShoppingCart> eventEnvelope) {
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

  @EventListener
  void handleProductItemRemovedFromShoppingCart(EventEnvelope<ProductItemRemovedFromShoppingCart> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope, view -> {
      var productItem = eventEnvelope.data().productItem();
      var existingProductItem = view.getProductItems().stream()
        .filter(x -> x.getProductId().equals(productItem.productId()))
        .findFirst();

      if (existingProductItem.isEmpty()) {
        // that's unexpected, but we have to live with that
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

  @EventListener
  void handleShoppingCartConfirmed(EventEnvelope<ShoppingCartConfirmed> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.setStatus(ShoppingCartStatus.Confirmed)
    );
  }

  @EventListener
  void handleShoppingCartCanceled(EventEnvelope<ShoppingCartCanceled> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.setStatus(ShoppingCartStatus.Canceled)
    );
  }
}
