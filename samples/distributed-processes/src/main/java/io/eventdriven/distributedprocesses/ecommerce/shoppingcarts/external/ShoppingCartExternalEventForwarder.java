package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.*;

public class ShoppingCartExternalEventForwarder {
  private final ShoppingCartSummaries summaries;
  private final MessageBus messageBus;

  public ShoppingCartExternalEventForwarder(
    ShoppingCartSummaries summaries,
    MessageBus messageBus
  ) {
    this.summaries = summaries;
    this.messageBus = messageBus;
  }

  public void onConfirmed(EventEnvelope<ShoppingCartConfirmed> envelope) {
    var event = envelope.data();
    var cart = get(event.shoppingCartId(), envelope.metadata().streamPosition());

    var externalEvent = new ShoppingCartFinalized(
      event.shoppingCartId(),
      cart.getClientId(),
      cart.getProductItems(),
      cart.getTotalPrice(),
      event.confirmedAt()
    );

    messageBus.publish(List.of(externalEvent));
  }

  private ShoppingCartSummary get(ShoppingCartId shoppingCartId, long streamPosition) {
    return summaries.get(shoppingCartId)
      .filter(summary -> summary.getVersion() >= streamPosition)
      .orElseThrow(() -> new IllegalStateException(
        "Summary of shopping cart '%s' has not reached position %d yet".formatted(shoppingCartId.value(), streamPosition)
      ));
  }
}
