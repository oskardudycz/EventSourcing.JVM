package io.eventdriven.core.eventstore;

import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
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
    private static final String READS = "eventdriven.eventstore.read.count";
    private static final String APPENDS = "eventdriven.eventstore.append.count";
    private static final List<String> READ_STATUSES = List.of("success", "failure");
    private static final List<String> APPEND_STATUSES = List.of("success", "conflict", "failure");
    private static final String SUBSCRIPTION_LAG = "eventdriven.subscription.lag";
    private static final String SUBSCRIPTION_NAME = "eventdriven.subscription.name";
    private static final String EVENT_TYPE = "eventdriven.event.type";
    private static final String APPEND_BATCH_SIZE = "eventdriven.eventstore.append.batch_size";

    private EventStoreObservations() {}

    public static EventStore.ReadResult observeRead(
            MessageContext context, String streamId, Supplier<EventStore.ReadResult> read) {
        var observation = context.observation("eventdriven.eventstore.read", "eventStore.readStream")
                .lowCardinalityKeyValue(OPERATION, "readStream")
                .highCardinalityKeyValue(STREAM_NAME, streamId)
                .start();

        try {
            var result = read.get();
            var events = result instanceof EventStore.ReadResult.Success success
                    ? success.events()
                    : List.<MessageEnvelope<Object>>of();

            count(context, READS, READ_STATUS, READ_STATUSES, statusOf(result));
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
            count(context, READS, READ_STATUS, READ_STATUSES, "failure");
            observation.lowCardinalityKeyValue(READ_STATUS, "failure").error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    public static EventStore.AppendResult observeAppend(
            MessageContext context, String streamId, Object[] events, Supplier<EventStore.AppendResult> append) {
        var observation = context.observation("eventdriven.eventstore.append", "eventStore.appendToStream")
                .lowCardinalityKeyValue(OPERATION, "appendToStream")
                .highCardinalityKeyValue(STREAM_NAME, streamId)
                .highCardinalityKeyValue(APPEND_BATCH_SIZE, String.valueOf(events.length))
                .start();

        try {
            var result = append.get();

            count(context, APPENDS, APPEND_STATUS, APPEND_STATUSES, statusOf(result));
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
            count(context, APPENDS, APPEND_STATUS, APPEND_STATUSES, "failure");
            observation.lowCardinalityKeyValue(APPEND_STATUS, "failure").error(failure);
            throw failure;
        } finally {
            observation.stop();
        }
    }

    // Registers the meters of a new subscription at zero, so that Prometheus sees their first change as an increase.
    public static void subscribed(MessageContext context, String subscription, List<Class<?>> types) {
        for (var type : types) context.meters().counter(APPENDED_EVENTS, EVENT_TYPE, type.getSimpleName());
        lagOf(context, subscription);
    }

    /** Records how long after its append the subscription handled the event. */
    public static void handled(MessageContext context, String subscription, MessageMetadata metadata) {
        var lag = Duration.between(metadata.stream().appendedAt(), Instant.now());
        lagOf(context, subscription).record(lag.isNegative() ? Duration.ZERO : lag);
    }

    // Registers every status at zero, so that Prometheus sees the first conflict or failure as an increase.
    private static void count(
            MessageContext context, String name, String statusTag, List<String> statuses, String status) {
        for (var each : statuses) {
            var counter = context.meters().counter(name, statusTag, each);
            if (each.equals(status)) counter.increment();
        }
    }

    private static Timer lagOf(MessageContext context, String subscription) {
        return Timer.builder(SUBSCRIPTION_LAG)
                .description("Time from appending an event to a subscription handling it")
                .tag(SUBSCRIPTION_NAME, subscription)
                .publishPercentileHistogram()
                .register(context.meters());
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
