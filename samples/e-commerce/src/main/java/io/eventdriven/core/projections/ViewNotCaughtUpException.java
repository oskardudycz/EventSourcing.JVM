package io.eventdriven.core.projections;

/** The document exists on the write side, but its view hasn't caught up with it yet. Asking again later will work. */
public class ViewNotCaughtUpException extends RuntimeException {
    public ViewNotCaughtUpException(String message) {
        super(message);
    }
}
