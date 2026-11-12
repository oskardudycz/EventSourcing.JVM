package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

public class MessageCatcherTests {
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher messages = new MessageCatcher();

  private final UUID orderId = UUID.randomUUID();

  @BeforeEach
  public void setUp() {
    messageBus.use(messages::catchMessage);
  }

  @Test
  public void recordsEventsPublishedOnTheBusInOrder() {
    // Given
    messageBus.subscribe(OrderPlaced.class, ignored -> {
    });

    // When
    messageBus.publish(List.of(new OrderPlaced(orderId, new String[]{"shoes"})));
    messageBus.publish(List.of(new OrderConfirmed(orderId)));

    // Then
    messages.shouldReceiveMessages(
      new OrderPlaced(orderId, new String[]{"shoes"}),
      new OrderConfirmed(orderId)
    );
  }

  @Test
  public void recordsCommandsAndEventsOnTheOneTranscript() {
    // Given
    messageBus.subscribe(
      ConfirmOrder.class,
      command -> messageBus.publish(List.of(new OrderConfirmed(command.orderId())))
    );

    // When
    messageBus.publish(List.of(new ConfirmOrder(orderId)));

    // Then
    messages.shouldReceiveMessages(
      new ConfirmOrder(orderId),
      new OrderConfirmed(orderId)
    );
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
    messageBus.subscribe(OrderConfirmed.class, ignored -> {
    });

    // When
    messageBus.publish(List.of(new OrderConfirmed(orderId)));

    // Then
    messages.shouldReceiveSingleEvent(new OrderConfirmed(orderId));
  }

  sealed interface OrderMessage {
  }

  record OrderPlaced(UUID orderId, String[] productNames) implements OrderMessage {
  }

  record OrderConfirmed(UUID orderId) implements OrderMessage {
  }

  record ConfirmOrder(UUID orderId) implements OrderMessage {
  }
}
