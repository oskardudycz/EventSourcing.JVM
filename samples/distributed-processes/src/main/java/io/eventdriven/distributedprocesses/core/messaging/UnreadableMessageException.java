package io.eventdriven.distributedprocesses.core.messaging;

public class UnreadableMessageException extends RuntimeException {
    public UnreadableMessageException(String messageType, Throwable cause) {
        super("Unable to read message of type " + messageType, cause);
    }

    public UnreadableMessageException(String messageType, String reason) {
        super("Unable to read message of type %s: %s".formatted(messageType, reason));
    }
}
