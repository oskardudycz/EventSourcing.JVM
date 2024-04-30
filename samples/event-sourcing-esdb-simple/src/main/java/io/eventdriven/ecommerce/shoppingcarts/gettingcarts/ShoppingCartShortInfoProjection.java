package io.eventdriven.ecommerce.shoppingcarts.gettingcarts;

import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.projections.JPAProjection;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.Opened;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAdded;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemoved;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.Confirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.Canceled;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ShoppingCartShortInfoProjection extends JPAProjection<ShoppingCartShortInfo, UUID> {
  protected ShoppingCartShortInfoProjection(ShoppingCartShortInfoRepository repository) {
    super(repository);
  }

  @EventListener
  void handleShoppingCartOpened(EventEnvelope<Opened> eventEnvelope) {
    add(eventEnvelope, () ->
      new ShoppingCartShortInfo(
        UUID.fromString(eventEnvelope.metadata().streamId()),
        eventEnvelope.data().clientId(),
        ShoppingCartStatus.Pending,
        0,
        0,
        eventEnvelope.metadata().streamPosition(),
        eventEnvelope.metadata().logPosition()
      )
    );
  }

  @EventListener
  void handleProductItemAddedToShoppingCart(EventEnvelope<ProductItemAdded> eventEnvelope) {
    getAndUpdate(UUID.fromString(eventEnvelope.metadata().streamId()), eventEnvelope,
      view -> view.increaseProducts(eventEnvelope.data().productItem())
    );
  }

  @EventListener
  void handleProductItemRemovedFromShoppingCart(EventEnvelope<ProductItemRemoved> eventEnvelope) {
    getAndUpdate(UUID.fromString(eventEnvelope.metadata().streamId()), eventEnvelope,
      view -> view.decreaseProducts(eventEnvelope.data().productItem())
    );
  }

  @EventListener
  void handleShoppingCartConfirmed(EventEnvelope<Confirmed> eventEnvelope) {
    getAndUpdate(UUID.fromString(eventEnvelope.metadata().streamId()), eventEnvelope,
      view -> view.setStatus(ShoppingCartStatus.Confirmed)
    );
  }

  @EventListener
  void handleShoppingCartCanceled(EventEnvelope<Canceled> eventEnvelope) {
    deleteById(UUID.fromString(eventEnvelope.metadata().streamId()));
  }
}
