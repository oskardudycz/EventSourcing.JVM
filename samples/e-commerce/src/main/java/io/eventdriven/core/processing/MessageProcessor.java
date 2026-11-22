package io.eventdriven.core.processing;

import io.eventdriven.core.messaging.MessageContext;
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

    /** What the processor does with a message: {@code projection}, {@code forwarder}, {@code reactor} or {@code processor}. */
    default String kind() {
        return "processor";
    }

    static List<Class<?>> canHandle(MessageProcessor... processors) {
        return Arrays.stream(processors)
                .flatMap(processor -> processor.canHandle().stream())
                .distinct()
                .toList();
    }

    /** Calls each processor that can handle the message, in its own span below the span that handles the message. */
    static Consumer<MessageEnvelope<Object>> handle(MessageContext context, MessageProcessor... processors) {
        return envelope -> {
            for (var processor : processors)
                if (processor.canHandle().stream().anyMatch(type -> type.isInstance(envelope.message())))
                    context.processing(
                            processor.kind(), processor.getClass().getSimpleName(), () -> processor.handle(envelope));
        };
    }
}
