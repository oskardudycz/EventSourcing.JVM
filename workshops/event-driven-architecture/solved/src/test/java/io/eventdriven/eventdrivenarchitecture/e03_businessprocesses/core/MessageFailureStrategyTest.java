package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy.Decision.DEAD_LETTER;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy.Decision.RETRY;
import static org.assertj.core.api.Assertions.assertThat;

public class MessageFailureStrategyTest {
  private final MessageFailureStrategy strategy = MessageFailureStrategy.retryUnlessUnreadable();

  @Test
  public void unreadableMessage_IsDeadLettered_EvenWhenWrappedByTheBroker() {
    var unreadable = new UnreadableMessageException("UnknownMessage", new IllegalArgumentException("no such type"));

    var decision = strategy.onFailure("UnknownMessage", Optional.empty(), new RuntimeException("Listener failed", unreadable));

    assertThat(decision).isEqualTo(DEAD_LETTER);
  }

  @Test
  public void handlerFailure_IsRetried() {
    var decision = strategy.onFailure("PaymentReceived", Optional.of("payment"), new IllegalStateException("Payment gateway unavailable"));

    assertThat(decision).isEqualTo(RETRY);
  }
}
