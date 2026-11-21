package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.util.List;

public class ShoppingCartExternalEventForwarder {
    private final ShoppingCartSummaries summaries;
    private final MessageBus messageBus;

    public ShoppingCartExternalEventForwarder(ShoppingCartSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    public void handle(EventEnvelope<Object> envelope) {
        switch (envelope.data()) {
            case ShoppingCartEvent.ShoppingCartCheckedOut event ->
                onCheckedOut(new EventEnvelope<>(event, envelope.metadata()));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShoppingCartsConfig.external,
                            payloadType = ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
                            description = "Tells other modules that the customer checked out a shopping cart."))
    public void onCheckedOut(EventEnvelope<ShoppingCartEvent.ShoppingCartCheckedOut> envelope) {
        var event = envelope.data();
        var cart = get(event.shoppingCartId(), envelope.metadata().streamPosition());

        var externalEvent = new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                event.shoppingCartId(),
                cart.getCustomerId(),
                cart.getProductItems(),
                cart.getTotalPrice(),
                event.checkedOutAt());

        messageBus.publish(List.of(externalEvent));
    }

    private ShoppingCartSummary get(ShoppingCartId shoppingCartId, long streamPosition) {
        return summaries
                .get(shoppingCartId)
                .filter(summary -> summary.getVersion() >= streamPosition)
                .orElseThrow(
                        () -> new IllegalStateException("Summary of shopping cart '%s' has not reached position %d yet"
                                .formatted(shoppingCartId.value(), streamPosition)));
    }
}
