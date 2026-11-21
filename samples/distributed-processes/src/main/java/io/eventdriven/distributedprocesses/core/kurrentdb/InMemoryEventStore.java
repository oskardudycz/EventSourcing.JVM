package io.eventdriven.distributedprocesses.core.kurrentdb;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageValues;
import io.kurrent.dbclient.Position;
import io.kurrent.dbclient.StreamState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class InMemoryEventStore implements EventStore {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryEventStore.class);

    private final MessageContext context;
    private final Map<String, List<LogEntry>> streams = new HashMap<>();
    private final List<LogEntry> log = new ArrayList<>();
    private final List<Subscription> middlewares = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Set<String> subscriptionNames = new HashSet<>();
    private boolean dispatching = false;

    public InMemoryEventStore(MessageContext context) {
        this.context = context;
    }

    @Override
    public ReadResult read(String streamId) {
        var stream = streams.get(streamId);

        if (stream == null) return new ReadResult.StreamDoesNotExist();

        if (stream.isEmpty()) return new ReadResult.NoEventsFound();

        return new ReadResult.Success(
                stream.stream().map(InMemoryEventStore::toEnvelope).toList());
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, StreamState.noStream(), events);
    }

    @Override
    public AppendResult append(String streamId, StreamState expectedRevision, Object... events) {
        var actualRevision = revisionOf(streamId);

        if (!matches(expectedRevision, actualRevision)) {
            return expectedRevision.equals(StreamState.noStream())
                    ? new AppendResult.StreamAlreadyExists(actualRevision)
                    : new AppendResult.Conflict(expectedRevision, actualRevision);
        }

        var stream = streams.computeIfAbsent(streamId, ignored -> new ArrayList<>());
        var values = context.inherit();

        for (var event : events) {
            var entry = new LogEntry(
                    streamId,
                    stream.size(),
                    log.size(),
                    serialize(event),
                    values.withMessageId(UUID.randomUUID().toString()));
            log.add(entry);
            stream.add(entry);
        }

        var lastLogPosition = log.size() - 1;
        var result = new AppendResult.Success(revisionOf(streamId), new Position(lastLogPosition, lastLogPosition));

        dispatch();

        return result;
    }

    @Override
    public MessageContext.Scope continueTraceOf(String stepName, EventMetadata lastEvent) {
        return context.continuing(stepName, lastEvent);
    }

    @Override
    public InMemoryEventStore subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        subscriptions.add(
                new Subscription(name, types.stream().map(Class::getTypeName).collect(Collectors.toSet()), handler));

        dispatch();

        return this;
    }

    public InMemoryEventStore use(Consumer<Object> middleware) {
        middlewares.add(new Subscription("middleware", null, envelope -> middleware.accept(envelope.data())));

        return this;
    }

    // Appending only writes; delivery runs after the outermost append. So an event appended by a
    // subscriber is delivered after the one it is handling, never in the middle of it. A subscriber
    // that throws keeps its checkpoint, gets nothing more in this run, and gets the same event again
    // on the next append.
    private void dispatch() {
        if (dispatching) return;

        dispatching = true;
        var failed = new HashSet<Subscription>();

        try {
            for (var position = firstUndeliveredPosition(); position < log.size(); position++) {
                var entry = log.get(position);

                for (var subscription : Stream.concat(middlewares.stream(), subscriptions.stream())
                        .toList()) {
                    if (subscription.checkpoint >= position || failed.contains(subscription)) continue;

                    if (subscription.accepts(entry)) {
                        var envelope = toEnvelope(entry);

                        try (var _ = context.handling(
                                "handle %s".formatted(envelope.data().getClass().getSimpleName()),
                                envelope.metadata())) {
                            subscription.handler.accept(envelope);
                        } catch (RuntimeException e) {
                            logger.error(
                                    "Subscriber '{}' failed on {} at log position {}; it will get it again on the next append",
                                    subscription.name,
                                    entry.storedEvent().eventType(),
                                    position,
                                    e);
                            failed.add(subscription);
                            continue;
                        }
                    }

                    subscription.checkpoint = position;
                }
            }
        } finally {
            dispatching = false;
        }
    }

    private int firstUndeliveredPosition() {
        return Stream.concat(middlewares.stream(), subscriptions.stream())
                .mapToInt(subscription -> subscription.checkpoint + 1)
                .min()
                .orElse(log.size());
    }

    private static EventEnvelope<Object> toEnvelope(LogEntry entry) {
        var values = entry.values();

        return new EventEnvelope<>(
                deserialize(entry.storedEvent()),
                new EventMetadata(
                        entry.streamId(),
                        entry.streamPosition(),
                        entry.logPosition(),
                        values.messageId(),
                        values.correlationId(),
                        values.causationId(),
                        values.traceId(),
                        values.spanId()));
    }

    private StreamState revisionOf(String streamId) {
        var stream = streams.getOrDefault(streamId, List.of());

        return stream.isEmpty() ? StreamState.noStream() : StreamState.streamRevision(stream.size() - 1);
    }

    private static boolean matches(StreamState expected, StreamState actual) {
        if (expected.equals(StreamState.any()) || expected.equals(actual)) return true;

        return expected.equals(StreamState.streamExists()) && !actual.equals(StreamState.noStream());
    }

    private static StoredEvent serialize(Object event) {
        try {
            return new StoredEvent(event.getClass().getTypeName(), mapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize event of type %s".formatted(event.getClass()), e);
        }
    }

    private static Object deserialize(StoredEvent envelope) {
        try {
            return mapper.readValue(envelope.json(), Class.forName(envelope.eventType()));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize event of type %s".formatted(envelope.eventType()), e);
        }
    }

    record StoredEvent(String eventType, String json) {}

    record LogEntry(
            String streamId, long streamPosition, long logPosition, StoredEvent storedEvent, MessageValues values) {}

    private static final class Subscription {
        private final String name;
        private final Set<String> eventTypes;
        private final Consumer<EventEnvelope<Object>> handler;
        private int checkpoint = -1;

        private Subscription(String name, Set<String> eventTypes, Consumer<EventEnvelope<Object>> handler) {
            this.name = name;
            this.eventTypes = eventTypes;
            this.handler = handler;
        }

        private boolean accepts(LogEntry entry) {
            return eventTypes == null || eventTypes.contains(entry.storedEvent().eventType());
        }
    }

    private static final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();
}
