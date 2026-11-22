package io.eventdriven.core.messaging;

public class UnreadableMessageException extends RuntimeException {
    public static final String UNKNOWN_TYPE = "unknown";

    public UnreadableMessageException(String messageType, Throwable cause) {
        super("Unable to read message of type " + messageType, cause);
    }

    public UnreadableMessageException(String messageType, String reason) {
        super("Unable to read message of type %s: %s".formatted(messageType, reason));
    }
}
