package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartSummary;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.*;

public class ShoppingCartExternalEventForwarder {
  private final ShoppingCartSummaries summaries;
  private final IntegrationEventBus eventBus;

  public ShoppingCartExternalEventForwarder(
    ShoppingCartSummaries summaries,
    IntegrationEventBus eventBus
  ) {
    this.summaries = summaries;
    this.eventBus = eventBus;
  }

  public void on(ShoppingCartConfirmed event) {
    var cart = get(event.shoppingCartId());

    var externalEvent = new ShoppingCartFinalized(
      event.shoppingCartId(),
      cart.getClientId(),
      cart.getProductItems(),
      cart.getTotalPrice(),
      event.confirmedAt()
    );

    eventBus.publish(externalEvent);
  }

  private ShoppingCartSummary get(ShoppingCartId shoppingCartId) {
    return summaries.get(shoppingCartId)
      .orElseThrow(() -> new IllegalStateException("Cannot enrich event, as shopping cart with id '%s' was not found".formatted(shoppingCartId)));
  }
}
