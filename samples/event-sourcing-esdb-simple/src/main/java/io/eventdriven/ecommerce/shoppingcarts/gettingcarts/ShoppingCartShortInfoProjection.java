package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartConfirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartCanceled;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ShoppingCartShortInfoProjection extends JPAProjection<ShoppingCartShortInfo, UUID> {
  protected ShoppingCartShortInfoProjection(ShoppingCartShortInfoRepository repository) {
    super(repository);
  }

  @EventListener
  void handleShoppingCartOpened(EventEnvelope<ShoppingCartOpened> eventEnvelope) {
    add(eventEnvelope, () ->
      new ShoppingCartShortInfo(
        eventEnvelope.data().shoppingCartId(),
        eventEnvelope.data().clientId(),
        ShoppingCart.Status.Pending,
        0,
        0,
        eventEnvelope.metadata().streamPosition(),
        eventEnvelope.metadata().logPosition()
      )
    );
  }

  @EventListener
  void handleProductItemAddedToShoppingCart(EventEnvelope<ProductItemAddedToShoppingCart> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.increaseProducts(eventEnvelope.data().productItem())
    );
  }

  @EventListener
  void handleProductItemRemovedFromShoppingCart(EventEnvelope<ProductItemRemovedFromShoppingCart> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.decreaseProducts(eventEnvelope.data().productItem())
    );
  }

  @EventListener
  void handleShoppingCartConfirmed(EventEnvelope<ShoppingCartConfirmed> eventEnvelope) {
    getAndUpdate(eventEnvelope.data().shoppingCartId(), eventEnvelope,
      view -> view.setStatus(ShoppingCart.Status.Confirmed)
    );
  }

  @EventListener
  void handleShoppingCartCanceled(EventEnvelope<ShoppingCartCanceled> eventEnvelope) {
    deleteById(eventEnvelope.data().shoppingCartId());
  }
}
