package io.eventdriven.distributedprocesses.core.eventstore;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageValues;
import io.eventdriven.distributedprocesses.core.messaging.UnreadableMessageException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private final List<Consumer<Object>> middlewares = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Set<String> subscriptionNames = new HashSet<>();
    private boolean dispatching = false;
    private int lastMiddlewarePosition = -1;

    public InMemoryEventStore(MessageContext context) {
        this.context = context;
    }

    @Override
    public synchronized ReadResult read(String streamId) {
        var stream = streams.get(streamId);

        if (stream == null || stream.isEmpty()) return new ReadResult.StreamDoesNotExist();

        return new ReadResult.Success(
                stream.stream().map(InMemoryEventStore::toEnvelope).toList());
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, ExpectedStreamVersion.noStream(), events);
    }

    @Override
    public synchronized AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        var actualVersion = versionOf(streamId);

        if (!matches(expectedVersion, actualVersion)) {
            return expectedVersion instanceof ExpectedStreamVersion.NoStream
                    ? new AppendResult.StreamAlreadyExists(actualVersion)
                    : new AppendResult.Conflict(expectedVersion, actualVersion);
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
        var result = new AppendResult.Success(versionOf(streamId), new Checkpoint(0, lastLogPosition));

        dispatch();

        return result;
    }

    @Override
    public MessageContext.Scope continueTraceOf(String stepName, EventMetadata lastEvent) {
        return context.continuing(stepName, lastEvent);
    }

    @Override
    public synchronized InMemoryEventStore subscribe(
            String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        subscriptions.add(
                new Subscription(name, types.stream().map(Class::getTypeName).collect(Collectors.toSet()), handler));

        dispatch();

        return this;
    }

    public synchronized InMemoryEventStore use(Consumer<Object> middleware) {
        middlewares.add(middleware);

        return this;
    }

    // Appending only writes; delivery runs after the outermost append. So an event appended by a
    // subscriber is delivered after the one it is handling, never in the middle of it. A subscriber
    // that throws keeps its checkpoint, gets nothing more in this run, and gets the same event again
    // on the next append. Middleware sees each event once, before its subscribers; its failure is not
    // retried.
    private void dispatch() {
        if (dispatching) return;

        dispatching = true;
        var failed = new HashSet<Subscription>();

        try {
            for (var position = firstUndeliveredPosition(); position < log.size(); position++) {
                var entry = log.get(position);

                if (position > lastMiddlewarePosition) {
                    lastMiddlewarePosition = position;
                    if (!middlewares.isEmpty()) {
                        var data = toEnvelope(entry).data();
                        for (var middleware : middlewares) middleware.accept(data);
                    }
                }

                for (var subscription : List.copyOf(subscriptions)) {
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
        return IntStream.concat(
                        IntStream.of(lastMiddlewarePosition + 1),
                        subscriptions.stream().mapToInt(subscription -> subscription.checkpoint + 1))
                .min()
                .getAsInt();
    }

    private static EventEnvelope<Object> toEnvelope(LogEntry entry) {
        var values = entry.values();

        return new EventEnvelope<>(
                deserialize(entry.storedEvent()),
                new EventMetadata(
                        entry.streamId(),
                        entry.streamPosition(),
                        entry.logPosition(),
                        0,
                        values.messageId(),
                        values.correlationId(),
                        values.causationId(),
                        values.traceId(),
                        values.spanId()));
    }

    private long versionOf(String streamId) {
        return streams.getOrDefault(streamId, List.of()).size() - 1;
    }

    private static boolean matches(ExpectedStreamVersion expected, long actual) {
        return switch (expected) {
            case ExpectedStreamVersion.Any ignored -> true;
            case ExpectedStreamVersion.NoStream ignored -> actual == -1;
            case ExpectedStreamVersion.StreamExists ignored -> actual != -1;
            case ExpectedStreamVersion.At at -> actual == at.version();
        };
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
            throw new UnreadableMessageException(envelope.eventType(), e);
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
            return eventTypes.contains(entry.storedEvent().eventType());
        }
    }

    private static final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();
}
