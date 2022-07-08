package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.events.EventBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;

import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartExternalEventForwarder {
  private final AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> store;
  private final EventBus eventBus;

  public ShoppingCartExternalEventForwarder(
    AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> store,
    EventBus eventBus
  ) {
    this.store = store;
    this.eventBus = eventBus;
  }

  public void on(ShoppingCartConfirmed event) {
    var cart = store.get(event.shoppingCartId())
      .orElseThrow(() -> new IllegalStateException("Cannot enrich event, as shopping cart with id '%s' was not found".formatted(event.shoppingCartId())));

    var externalEvent = new ShoppingCartFinalized(
      event.shoppingCartId(),
      cart.clientId(),
      cart.productItems(),
      cart.totalPrice(),
      event.confirmedAt()
    );

    eventBus.publish(externalEvent);
  }
}
