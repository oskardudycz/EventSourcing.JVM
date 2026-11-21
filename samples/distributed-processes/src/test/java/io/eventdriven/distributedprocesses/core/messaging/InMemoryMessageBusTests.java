package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.testing.TestTracing;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InMemoryMessageBusTests extends MessageBusTests {
    @Override
    protected MessageBus createBus() {
        return new InMemoryMessageBus(TestTracing.untraced());
    }

    @Test
    public void deliversMessagesInTheOrderTheyWereGiven() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));
        messageBus.subscribe(orders, ConfirmOrder.class, command -> log.add("confirm"));

        messageBus.publish(List.of(new ConfirmOrder("order-1"), new OrderPlaced("order-1")));

        assertThat(log).containsExactly("confirm", "placed");
    }

    @Test
    public void deliversDepthFirstSoNestedPublishCompletesBeforeSubscriberReturns() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            log.add("placed:start");
            messageBus.publish(List.of(new ConfirmOrder(event.id())));
            log.add("placed:end");
        });
        messageBus.subscribe(orders, ConfirmOrder.class, command -> log.add("confirm"));
        messageBus.subscribe(orders, OrderCanceled.class, event -> log.add("canceled"));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new OrderCanceled("order-1")));

        assertThat(log).containsExactly("placed:start", "confirm", "placed:end", "canceled");
    }

    @Test
    public void runsMiddlewareForEveryMessageBeforeSubscribersInRegistrationOrder() {
        messageBus.use(
                message -> log.add("first middleware:" + message.getClass().getSimpleName()));
        messageBus.use(
                message -> log.add("second middleware:" + message.getClass().getSimpleName()));
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));
        messageBus.subscribe(orders, ConfirmOrder.class, command -> log.add("confirm"));

        messageBus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

        assertThat(log)
                .containsExactly(
                        "first middleware:OrderPlaced",
                        "second middleware:OrderPlaced",
                        "placed",
                        "first middleware:ConfirmOrder",
                        "second middleware:ConfirmOrder",
                        "confirm");
    }
}
