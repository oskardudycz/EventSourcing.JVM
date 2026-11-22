package io.eventdriven.core.messaging;

import java.util.Optional;

public interface MessageFailureStrategy {
    enum Decision {
        RETRY,
        DEAD_LETTER
    }

    Decision onFailure(String messageType, Optional<Object> message, Throwable error);

    static MessageFailureStrategy retryUnlessUnreadable() {
        return (messageType, message, error) ->
                isCausedBy(error, UnreadableMessageException.class) ? Decision.DEAD_LETTER : Decision.RETRY;
    }

    private static boolean isCausedBy(Throwable error, Class<? extends Throwable> type) {
        for (var current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
