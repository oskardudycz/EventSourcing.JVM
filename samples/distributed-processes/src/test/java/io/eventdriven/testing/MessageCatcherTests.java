package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class MessageCatcherTests {
  private final InMemoryEventBus eventBus = new InMemoryEventBus();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher messages = new MessageCatcher();

  private final UUID orderId = UUID.randomUUID();

  @BeforeEach
  public void setUp() {
    eventBus.use(messages::catchMessage);
    commandBus.use(messages::catchMessage);
  }

  @Test
  public void recordsEventsPublishedOnTheEventBusInOrder() {
    // Given
    eventBus.subscribe(OrderPlaced.class, ignored -> {
    });

    // When
    eventBus.publish(new OrderPlaced(orderId, new String[]{"shoes"}));
    eventBus.publish(new OrderConfirmed(orderId));

    // Then
    messages.shouldReceiveMessages(
      new OrderPlaced(orderId, new String[]{"shoes"}),
      new OrderConfirmed(orderId)
    );
  }

  @Test
  public void recordsCommandsAndEventsOnTheOneTranscript() {
    // Given
    commandBus.handle(ConfirmOrder.class, command -> eventBus.publish(new OrderConfirmed(command.orderId())));

    // When
    commandBus.send(new ConfirmOrder(orderId));

    // Then
    messages.shouldReceiveMessages(
      new ConfirmOrder(orderId),
      new OrderConfirmed(orderId)
    );
  }

  @Test
  public void resetForgetsWhatWasRecordedSoFar() {
    // Given
    eventBus.publish(new OrderConfirmed(orderId));

    // When
    messages.reset();

    // Then
    messages.shouldNotReceiveAnyEvent();
  }

  @Test
  public void recordsASingleEventWhenOnlyOneWasPublished() {
    // Given
    eventBus.subscribe(OrderConfirmed.class, ignored -> {
    });

    // When
    eventBus.publish(new OrderConfirmed(orderId));

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
