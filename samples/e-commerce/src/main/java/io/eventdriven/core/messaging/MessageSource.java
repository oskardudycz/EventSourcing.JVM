package io.eventdriven.core.messaging;

import io.eventdriven.core.eventstore.Checkpoint;

public sealed interface MessageSource {
    record InStream(String streamId, long streamPosition, long logPosition, long transactionId)
            implements MessageSource {
        public Checkpoint checkpoint() {
            return new Checkpoint(transactionId, logPosition);
        }
    }

    record InQueue(String queue) implements MessageSource {}

    record OnChannel(String channel) implements MessageSource {}

    /** A message published with no declared channel: publishing it fails right after the middleware sees it. */
    record Unrouted() implements MessageSource {}
}
