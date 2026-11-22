package io.eventdriven.ecommerce.shoppingcarts.external;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Forwarder;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.util.List;

public class ShoppingCartExternalEventForwarder implements Forwarder {
    private final ShoppingCartSummaries summaries;
    private final MessageBus messageBus;

    public ShoppingCartExternalEventForwarder(ShoppingCartSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(ShoppingCartEvent.ShoppingCartCheckedOut.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        switch (envelope.message()) {
            case ShoppingCartEvent.ShoppingCartCheckedOut event ->
                onCheckedOut(new MessageEnvelope<>(event, envelope.metadata()));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.message()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShoppingCartsConfig.external,
                            payloadType = ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
                            description = "Tells other modules that the customer checked out a shopping cart."))
    public void onCheckedOut(MessageEnvelope<ShoppingCartEvent.ShoppingCartCheckedOut> envelope) {
        var event = envelope.message();
        var cart = get(event.shoppingCartId(), envelope.metadata().stream().streamPosition());

        var externalEvent = new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                event.shoppingCartId(),
                cart.getCustomerId(),
                cart.getProductItems(),
                cart.getTotalPrice(),
                event.checkedOutAt());

        messageBus.publish(List.of(externalEvent));
    }

    private ShoppingCartSummary get(ShoppingCart.Id shoppingCartId, long streamPosition) {
        return summaries
                .get(shoppingCartId)
                .filter(summary -> summary.getVersion() >= streamPosition)
                .orElseThrow(
                        () -> new IllegalStateException("Summary of shopping cart '%s' has not reached position %d yet"
                                .formatted(shoppingCartId.value(), streamPosition)));
    }
}
