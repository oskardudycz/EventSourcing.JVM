package io.eventdriven.core.eventstore;

import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class EventStoreObservations {
    private static final String OPERATION = "eventdriven.eventstore.operation";
    private static final String STREAM_NAME = "eventdriven.stream.name";
    private static final String STREAM_VERSION_AFTER = "eventdriven.stream.version.after";
    private static final String READ_STATUS = "eventdriven.eventstore.read.status";
    private static final String READ_EVENT_COUNT = "eventdriven.eventstore.read.event_count";
    private static final String READ_EVENT_TYPES = "eventdriven.eventstore.read.event_types";
    private static final String APPEND_STATUS = "eventdriven.eventstore.append.status";
    private static final String APPENDED_EVENTS = "eventdriven.event.appending.count";
    private static final String EVENT_TYPE = "eventdriven.event.type";
    private static final String APPEND_BATCH_SIZE = "eventdriven.eventstore.append.batch_size";

    private EventStoreObservations() {}

    public static EventStore.ReadResult observeRead(
            MessageContext context, String streamId, Supplier<EventStore.ReadResult> read) {
        var observation = context.observation("eventStore.readStream")
                .lowCardinalityKeyValue(OPERATION, "readStream")
                .highCardinalityKeyValue(STREAM_NAME, streamId)
                .start();

        try {
            var result = read.get();
            var events = result instanceof EventStore.ReadResult.Success success
                    ? success.events()
                    : List.<MessageEnvelope<Object>>of();

            observation
                    .lowCardinalityKeyValue(READ_STATUS, statusOf(result))
                    .highCardinalityKeyValue(READ_EVENT_COUNT, String.valueOf(events.size()))
                    .highCardinalityKeyValue(
                            READ_EVENT_TYPES,
                            events.stream()
                                    .map(event -> event.message().getClass().getSimpleName())
                                    .distinct()
                                    .collect(Collectors.joining(",")));
            if (result instanceof EventStore.ReadResult.UnexpectedFailure(var failure)) observation.error(failure);

            return result;
        } catch (RuntimeException failure) {
            observation.lowCardinalityKeyValue(READ_STATUS, "failure").error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    public static EventStore.AppendResult observeAppend(
            MessageContext context, String streamId, Object[] events, Supplier<EventStore.AppendResult> append) {
        var observation = context.observation("eventStore.appendToStream")
                .lowCardinalityKeyValue(OPERATION, "appendToStream")
                .highCardinalityKeyValue(STREAM_NAME, streamId)
                .highCardinalityKeyValue(APPEND_BATCH_SIZE, String.valueOf(events.length))
                .start();

        try {
            var result = append.get();

            observation.lowCardinalityKeyValue(APPEND_STATUS, statusOf(result));
            switch (result) {
                case EventStore.AppendResult.Success success -> {
                    observation.highCardinalityKeyValue(
                            STREAM_VERSION_AFTER, String.valueOf(success.nextStreamVersion()));
                    for (var event : events)
                        context.meters()
                                .counter(
                                        APPENDED_EVENTS,
                                        EVENT_TYPE,
                                        event.getClass().getSimpleName())
                                .increment();
                }
                case EventStore.AppendResult.UnexpectedFailure(var failure) -> observation.error(failure);
                default -> {}
            }

            return result;
        } catch (RuntimeException failure) {
            observation.lowCardinalityKeyValue(APPEND_STATUS, "failure").error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    private static String statusOf(EventStore.ReadResult result) {
        return result instanceof EventStore.ReadResult.UnexpectedFailure ? "failure" : "success";
    }

    private static String statusOf(EventStore.AppendResult result) {
        return switch (result) {
            case EventStore.AppendResult.Success ignored -> "success";
            case EventStore.AppendResult.StreamAlreadyExists ignored -> "conflict";
            case EventStore.AppendResult.Conflict ignored -> "conflict";
            case EventStore.AppendResult.UnexpectedFailure ignored -> "failure";
        };
    }
}
