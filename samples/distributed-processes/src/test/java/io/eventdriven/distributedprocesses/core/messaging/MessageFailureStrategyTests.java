package io.eventdriven.distributedprocesses.core.messaging;

import static io.eventdriven.distributedprocesses.core.messaging.MessageFailureStrategy.Decision.DEAD_LETTER;
import static io.eventdriven.distributedprocesses.core.messaging.MessageFailureStrategy.Decision.RETRY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MessageFailureStrategyTests {
    private final MessageFailureStrategy strategy = MessageFailureStrategy.retryUnlessUnreadable();

    @Test
    public void unreadableMessageIsDeadLetteredEvenWhenWrappedByTheBroker() {
        var unreadable = new UnreadableMessageException("UnknownMessage", new IllegalArgumentException("no such type"));

        var decision = strategy.onFailure(
                "UnknownMessage", Optional.empty(), new RuntimeException("Listener failed", unreadable));

        assertThat(decision).isEqualTo(DEAD_LETTER);
    }

    @Test
    public void handlerFailureIsRetried() {
        var decision = strategy.onFailure(
                "PaymentReceived", Optional.of("payment"), new IllegalStateException("Payment gateway unavailable"));

        assertThat(decision).isEqualTo(RETRY);
    }
}
