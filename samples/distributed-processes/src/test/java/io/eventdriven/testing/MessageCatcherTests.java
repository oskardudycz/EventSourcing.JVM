package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageCatcherTests {
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced())
            .routes(new MessageRoutes()
                    .routeTarget(MessageRoutes.inPackage(MessageCatcherTests.class.getPackageName()), "orders"))
            .channel("orders")
            .bind("orders", "orders", "#");
    private final MessageCatcher messages = new MessageCatcher();

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        messageBus.use(messages::catchMessage);
    }

    @Test
    public void recordsEventsPublishedOnTheBusInOrder() {
        // Given
        messageBus.subscribe("orders", OrderPlaced.class, ignored -> {});

        // When
        messageBus.publish(List.of(new OrderPlaced(orderId, new String[] {"shoes"})));
        messageBus.publish(List.of(new OrderConfirmed(orderId)));

        // Then
        messages.shouldReceiveMessages(new OrderPlaced(orderId, new String[] {"shoes"}), new OrderConfirmed(orderId));
    }

    @Test
    public void recordsCommandsAndEventsOnTheOneTranscript() {
        // Given
        messageBus.subscribe(
                "orders",
                ConfirmOrder.class,
                command -> messageBus.publish(List.of(new OrderConfirmed(command.orderId()))));

        // When
        messageBus.publish(List.of(new ConfirmOrder(orderId)));

        // Then
        messages.shouldReceiveMessages(new ConfirmOrder(orderId), new OrderConfirmed(orderId));
    }

    @Test
    public void resetForgetsWhatWasRecordedSoFar() {
        // Given
        messageBus.publish(List.of(new OrderConfirmed(orderId)));

        // When
        messages.reset();

        // Then
        messages.shouldNotReceiveAnyEvent();
    }

    @Test
    public void recordsASingleEventWhenOnlyOneWasPublished() {
        // Given
        messageBus.subscribe("orders", OrderConfirmed.class, ignored -> {});

        // When
        messageBus.publish(List.of(new OrderConfirmed(orderId)));

        // Then
        messages.shouldReceiveSingleEvent(new OrderConfirmed(orderId));
    }

    sealed interface OrderMessage {}

    record OrderPlaced(UUID orderId, String[] productNames) implements OrderMessage {}

    record OrderConfirmed(UUID orderId) implements OrderMessage {}

    record ConfirmOrder(UUID orderId) implements OrderMessage {}
}
