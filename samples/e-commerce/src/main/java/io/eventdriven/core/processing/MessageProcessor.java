package io.eventdriven.core.processing;

import io.eventdriven.core.messaging.MessageEnvelope;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles the messages of the types it can handle. A subscription to an event store or a queue can run several
 * processors: for each message, it calls in order every processor that can handle it.
 */
public interface MessageProcessor {
    List<Class<?>> canHandle();

    void handle(MessageEnvelope<Object> envelope);

    static List<Class<?>> canHandle(MessageProcessor... processors) {
        return Arrays.stream(processors)
                .flatMap(processor -> processor.canHandle().stream())
                .distinct()
                .toList();
    }

    static Consumer<MessageEnvelope<Object>> handle(MessageProcessor... processors) {
        return envelope -> {
            for (var processor : processors)
                if (processor.canHandle().stream().anyMatch(type -> type.isInstance(envelope.message())))
                    processor.handle(envelope);
        };
    }
}
