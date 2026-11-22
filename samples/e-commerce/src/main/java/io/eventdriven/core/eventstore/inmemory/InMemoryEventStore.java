package io.eventdriven.core.eventstore.inmemory;

import io.eventdriven.core.eventstore.Checkpoint;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.EventStoreObservations;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.eventstore.SubscriptionProgress;
import io.eventdriven.core.eventstore.SubscriptionsCatchUp;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageSource;
import io.eventdriven.core.messaging.MessageValues;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.processing.MessageProcessor;
import java.time.Duration;
import java.time.Instant;
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

public class InMemoryEventStore implements EventStore {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryEventStore.class);

    private final MessageContext context;
    private final MessageSerializer serializer;
    private final Map<String, List<LogEntry>> streams = new HashMap<>();
    private final List<LogEntry> log = new ArrayList<>();
    private final List<Consumer<Object>> middlewares = new ArrayList<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private final SubscriptionsCatchUp catchUp = new SubscriptionsCatchUp();
    private final Set<String> subscriptionNames = new HashSet<>();
    private boolean dispatching = false;
    private int lastMiddlewarePosition = -1;

    public InMemoryEventStore(MessageContext context, MessageSerializer serializer) {
        this.context = context;
        this.serializer = serializer;
    }

    @Override
    public ReadResult read(String streamId) {
        return EventStoreObservations.observeRead(context, streamId, () -> readStream(streamId));
    }

    private synchronized ReadResult readStream(String streamId) {
        var stream = streams.get(streamId);

        if (stream == null || stream.isEmpty()) return new ReadResult.StreamDoesNotExist();

        return new ReadResult.Success(stream.stream().map(this::toEnvelope).toList());
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, ExpectedStreamVersion.noStream(), events);
    }

    @Override
    public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        return EventStoreObservations.observeAppend(
                context, streamId, events, () -> appendToStream(streamId, expectedVersion, events));
    }

    private synchronized AppendResult appendToStream(
            String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        if (events.length == 0)
            throw new IllegalArgumentException("Cannot append no events to stream %s".formatted(streamId));

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
                    values.withMessageId(UUID.randomUUID().toString()),
                    Instant.now());
            log.add(entry);
            stream.add(entry);
            catchUp.appended(serializer.typeNameOf(event.getClass()), new Checkpoint(0, entry.logPosition()));
        }

        var lastLogPosition = log.size() - 1;
        var result = new AppendResult.Success(versionOf(streamId), new Checkpoint(0, lastLogPosition));

        dispatch();

        return result;
    }

    @Override
    public Subscription subscribe(String name, MessageProcessor... processors) {
        return subscribe(name, MessageProcessor.canHandle(processors), MessageProcessor.handle(context, processors));
    }

    @Override
    public synchronized Subscription subscribe(
            String name, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        EventStoreObservations.subscribed(context, name, types);

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        var progress = new SubscriptionProgress(name);
        var typeNames = types.stream()
                .flatMap(type -> serializer.typeNamesOf(type).stream())
                .collect(Collectors.toSet());
        subscribers.add(new Subscriber(name, typeNames, handler, progress));
        catchUp.subscribed(typeNames, progress);

        dispatch();

        return progress;
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
        var failed = new HashSet<Subscriber>();

        try {
            for (var position = firstUndeliveredPosition(); position < log.size(); position++) {
                var entry = log.get(position);

                if (position > lastMiddlewarePosition) {
                    lastMiddlewarePosition = position;
                    if (!middlewares.isEmpty()) {
                        var data = toEnvelope(entry).message();
                        for (var middleware : middlewares) middleware.accept(data);
                    }
                }

                for (var subscriber : List.copyOf(subscribers)) {
                    if (subscriber.checkpoint >= position || failed.contains(subscriber)) continue;

                    if (subscriber.accepts(entry)) {
                        try {
                            var envelope = toEnvelope(entry);

                            try (var _ = context.handling(
                                    "handle %s"
                                            .formatted(envelope.message()
                                                    .getClass()
                                                    .getSimpleName()),
                                    envelope.metadata())) {
                                subscriber.handler.accept(envelope);
                                EventStoreObservations.handled(context, subscriber.name, envelope.metadata());
                                subscriber.progress.handled(new Checkpoint(0, position));
                            }
                        } catch (RuntimeException e) {
                            logger.error(
                                    "Subscriber '{}' failed on {} at log position {}; it will get it again on the next append",
                                    subscriber.name,
                                    entry.storedEvent().eventType(),
                                    position,
                                    e);
                            failed.add(subscriber);
                            continue;
                        }
                    }

                    subscriber.checkpoint = position;
                }
            }
        } finally {
            dispatching = false;
        }
    }

    private int firstUndeliveredPosition() {
        return IntStream.concat(
                        IntStream.of(lastMiddlewarePosition + 1),
                        subscribers.stream().mapToInt(subscriber -> subscriber.checkpoint + 1))
                .min()
                .getAsInt();
    }

    private MessageEnvelope<Object> toEnvelope(LogEntry entry) {
        var values = entry.values();

        return new MessageEnvelope<>(
                deserialize(entry.storedEvent()),
                new MessageMetadata(
                        values.messageId(),
                        values.correlationId(),
                        values.causationId(),
                        values.traceId(),
                        values.spanId(),
                        new MessageSource.InStream(
                                entry.streamId(), entry.streamPosition(), entry.logPosition(), 0, entry.appendedAt())));
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

    private StoredEvent serialize(Object event) {
        try {
            return new StoredEvent(serializer.typeNameOf(event.getClass()), serializer.write(event));
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize event of type %s".formatted(event.getClass()), e);
        }
    }

    private Object deserialize(StoredEvent event) {
        return serializer.read(event.eventType(), event.data());
    }

    record StoredEvent(String eventType, byte[] data) {}

    record LogEntry(
            String streamId,
            long streamPosition,
            long logPosition,
            StoredEvent storedEvent,
            MessageValues values,
            Instant appendedAt) {}

    private static final class Subscriber {
        private final String name;
        private final Set<String> eventTypes;
        private final Consumer<MessageEnvelope<Object>> handler;
        private final SubscriptionProgress progress;
        private int checkpoint = -1;

        private Subscriber(
                String name,
                Set<String> eventTypes,
                Consumer<MessageEnvelope<Object>> handler,
                SubscriptionProgress progress) {
            this.name = name;
            this.eventTypes = eventTypes;
            this.handler = handler;
            this.progress = progress;
        }

        private boolean accepts(LogEntry entry) {
            return eventTypes.contains(entry.storedEvent().eventType());
        }
    }

    @Override
    public void whenCaughtUp(Duration timeout) {
        catchUp.whenCaughtUp(timeout);
    }
}
