package io.eventdriven.distributedprocesses.core.messaging;

import io.eventdriven.testing.TestTracing;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InMemoryMessageBusTests extends MessageBusTests {
    private final TestTracing tracing = new TestTracing();

    @Override
    protected MessageBus createBus(
            List<MessageRoutes> routes, List<MessageChannel> channels, List<MessageQueue> queues) {
        return new InMemoryMessageBus(tracing.messageContext(), routes, channels, queues);
    }

    @Override
    protected List<SpanData> finishedSpans() {
        return tracing.finishedSpans();
    }

    @Test
    public void deliversMessagesInTheOrderTheyWereGiven() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        messageBus.subscribe(orders, ConfirmOrder.class, messages::catchMessage);

        messageBus.publish(List.of(new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

        messages.shouldReceiveMessages(new ConfirmOrder("order-1"), new OrderPlaced("order-1"));
    }

    @Test
    public void deliversDepthFirstSoNestedPublishCompletesBeforeSubscriberReturns() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            messages.catchMessage(new Handled("placed:start", event));
            messageBus.publish(List.of(new ConfirmOrder(event.id())));
            messages.catchMessage(new Handled("placed:end", event));
        });
        messageBus.subscribe(
                orders, ConfirmOrder.class, command -> messages.catchMessage(new Handled("confirm", command)));
        messageBus.subscribe(
                orders, OrderCanceled.class, event -> messages.catchMessage(new Handled("canceled", event)));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new OrderCanceled("order-1")));

        messages.shouldReceiveMessages(
                new Handled("placed:start", new OrderPlaced("order-1")),
                new Handled("confirm", new ConfirmOrder("order-1")),
                new Handled("placed:end", new OrderPlaced("order-1")),
                new Handled("canceled", new OrderCanceled("order-1")));
    }

    @Test
    public void runsMiddlewareForEveryMessageBeforeSubscribersInRegistrationOrder() {
        messageBus.use(message -> messages.catchMessage(new Handled("first middleware", message)));
        messageBus.use(message -> messages.catchMessage(new Handled("second middleware", message)));
        messageBus.subscribe(orders, OrderPlaced.class, event -> messages.catchMessage(new Handled("placed", event)));
        messageBus.subscribe(
                orders, ConfirmOrder.class, command -> messages.catchMessage(new Handled("confirm", command)));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

        messages.shouldReceiveMessages(
                new Handled("first middleware", new OrderPlaced("order-1")),
                new Handled("second middleware", new OrderPlaced("order-1")),
                new Handled("placed", new OrderPlaced("order-1")),
                new Handled("first middleware", new ConfirmOrder("order-1")),
                new Handled("second middleware", new ConfirmOrder("order-1")),
                new Handled("confirm", new ConfirmOrder("order-1")));
    }
}
