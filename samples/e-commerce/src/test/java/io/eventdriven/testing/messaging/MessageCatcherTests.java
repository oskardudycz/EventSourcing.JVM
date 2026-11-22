package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.testing.tracing.TestTracing;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageCatcherTests {
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(new MessageRoutes()
                    .routeTarget(MessageRoutes.inPackage(MessageCatcherTests.class.getPackageName()), "orders")),
            List.of(new MessageChannel("orders")),
            List.of(new MessageQueue("orders", "orders", "#")));
    private final MessageCatcher messages = new MessageCatcher();

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        messageBus.use(envelope -> messages.catchMessage(envelope.message()));
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
