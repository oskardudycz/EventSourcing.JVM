package io.eventdriven.core.eventstore.kurrentdb;

import io.eventdriven.core.eventstore.Checkpoint;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.eventstore.SubscriptionProgress;
import io.eventdriven.core.eventstore.SubscriptionsCatchUp;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.EventMetadata;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageValues;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.kurrent.dbclient.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.util.backoff.BackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

public class KurrentDBEventStore implements EventStore, AutoCloseable {
    private static final String CORRELATION_ID = "$correlationId";
    private static final String CAUSATION_ID = "$causationId";
    private static final String TRACE_ID = "$traceId";
    private static final String SPAN_ID = "$spanId";
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    @Override
    public ReadResult read(String streamId) {
        try {
            var result =
                    eventStore.readStream(streamId, ReadStreamOptions.get()).get();

            var events = result.getEvents().stream()
                    .map(resolvedEvent -> new EventEnvelope<>(deserialize(resolvedEvent), metadataOf(resolvedEvent)))
                    .toList();

            return new ReadResult.Success(events);
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof StreamNotFoundException) {
                return new ReadResult.StreamDoesNotExist();
            }
            return new ReadResult.UnexpectedFailure(e);
        }
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, ExpectedStreamVersion.noStream(), events);
    }

    @Override
    public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        if (events.length == 0)
            throw new IllegalArgumentException("Cannot append no events to stream %s".formatted(streamId));

        var eventsToAppend = serialize(events);
        var observation = Observation.createNotStarted("streams.append", observations)
                .lowCardinalityKeyValue("db.system", "kurrentdb")
                .lowCardinalityKeyValue("db.operation", "streams.append")
                .highCardinalityKeyValue("db.kurrentdb.stream", streamId)
                .start();

        try (var _ = observation.openScope()) {
            var result = eventStore
                    .appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(toStreamState(expectedVersion)),
                            eventsToAppend.iterator())
                    .get();

            var nextRevision = result.getNextExpectedRevision().toRawLong();
            appendedRanges.add(new AppendedRange(streamId, nextRevision - events.length + 1, events.length));

            return new AppendResult.Success(nextRevision, checkpointOf(result.getLogPosition()));
        } catch (InterruptedException | ExecutionException e) {
            observation.error(e);

            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersion) {
                var actual = wrongExpectedVersion.getActualState().toRawLong();

                return expectedVersion instanceof ExpectedStreamVersion.NoStream
                        ? new AppendResult.StreamAlreadyExists(actual)
                        : new AppendResult.Conflict(expectedVersion, actual);
            }
            return new AppendResult.UnexpectedFailure(e);
        } finally {
            observation.stop();
        }
    }

    @Override
    public Subscription subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        var typeNames = types.stream()
                .flatMap(type -> serializer.typeNamesOf(type).stream())
                .map(KurrentDBEventStore::escapedForRegularExpression)
                .collect(Collectors.joining("|"));

        var filter = SubscriptionFilter.newBuilder()
                .withEventTypeRegularExpression("^(%s)$".formatted(typeNames))
                .build();

        var progress = new SubscriptionProgress(name);
        checkpoints.lastProcessed(name).ifPresent(progress::handled);
        var subscription = new KurrentDBSubscriptionToAll(
                eventStore,
                name,
                () -> optionsAfterCheckpointOf(name).filter(filter),
                resolvedEvent -> {
                    var event = deserialize(resolvedEvent);
                    var metadata = metadataOf(resolvedEvent);

                    try (var _ = context.handling(
                            "handle %s".formatted(event.getClass().getSimpleName()), metadata)) {
                        checkpoints.handle(
                                name,
                                metadata.logPosition(),
                                () -> handler.accept(new EventEnvelope<>(event, metadata)));
                    }

                    progress.handled(metadata.checkpoint());
                },
                backOff,
                scheduler);
        subscriptions.add(subscription);
        catchUp.subscribed(
                types.stream()
                        .flatMap(type -> serializer.typeNamesOf(type).stream())
                        .collect(Collectors.toSet()),
                progress);
        subscription.start();

        return progress;
    }

    @Override
    public void close() {
        subscriptions.forEach(KurrentDBSubscriptionToAll::close);
        scheduler.shutdownNow();
        awaitSchedulerTermination();
        if (ownsClient) eventStore.shutdown();
    }

    private void awaitSchedulerTermination() {
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SubscribeToAllOptions optionsAfterCheckpointOf(String name) {
        var options = SubscribeToAllOptions.get();
        var checkpoint = checkpoints.lastProcessedPosition(name);

        return checkpoint.isPresent()
                ? options.fromPosition(new Position(checkpoint.getAsLong(), checkpoint.getAsLong()))
                : options.fromStart();
    }

    private List<EventData> serialize(Object[] events) {
        var values = context.inherit();

        return Arrays.stream(events)
                .map(event ->
                        serialize(event, values.withMessageId(UUID.randomUUID().toString())))
                .toList();
    }

    private EventData serialize(Object event, MessageValues values) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put(CORRELATION_ID, values.correlationId());
        metadata.put(CAUSATION_ID, values.causationId());
        if (values.traceId() != null) metadata.put(TRACE_ID, values.traceId());
        if (values.spanId() != null) metadata.put(SPAN_ID, values.spanId());

        return EventDataBuilder.json(
                        UUID.fromString(values.messageId()),
                        serializer.typeNameOf(event.getClass()),
                        serializer.write(event))
                .metadataAsBytes(serializer.jsonMapper().writeValueAsBytes(metadata))
                .build();
    }

    private static String escapedForRegularExpression(String typeName) {
        return typeName.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    private static StreamState toStreamState(ExpectedStreamVersion expectedVersion) {
        return switch (expectedVersion) {
            case ExpectedStreamVersion.NoStream ignored -> StreamState.noStream();
            case ExpectedStreamVersion.Any ignored -> StreamState.any();
            case ExpectedStreamVersion.StreamExists ignored -> StreamState.streamExists();
            case ExpectedStreamVersion.At at -> StreamState.streamRevision(at.version());
        };
    }

    private static Checkpoint checkpointOf(Position position) {
        return new Checkpoint(0, position.getCommitUnsigned());
    }

    private Object deserialize(ResolvedEvent resolvedEvent) {
        var recorded = resolvedEvent.getEvent();

        return serializer.read(recorded.getEventType(), recorded.getEventData());
    }

    private Map<String, String> userMetadataOf(ResolvedEvent resolvedEvent) {
        var raw = resolvedEvent.getEvent().getUserMetadata();

        if (raw == null || raw.length == 0) return Map.of();

        var values = new LinkedHashMap<String, String>();
        try {
            var parsed = serializer.jsonMapper().readValue(raw, METADATA_TYPE);

            if (parsed == null) return Map.of();

            parsed.forEach((key, value) -> {
                if (value instanceof String || value instanceof Number || value instanceof Boolean)
                    values.put(key, value.toString());
            });
        } catch (JacksonException e) {
            return Map.of();
        }
        return values;
    }

    private EventMetadata metadataOf(ResolvedEvent resolvedEvent) {
        var recorded = resolvedEvent.getEvent();
        var userMetadata = userMetadataOf(resolvedEvent);
        var messageId = recorded.getEventId().toString();
        var correlationId = userMetadata.getOrDefault(CORRELATION_ID, messageId);

        return new EventMetadata(
                recorded.getStreamId(),
                recorded.getRevision(),
                recorded.getPosition().getCommitUnsigned(),
                0,
                messageId,
                correlationId,
                userMetadata.getOrDefault(CAUSATION_ID, correlationId),
                userMetadata.get(TRACE_ID),
                userMetadata.get(SPAN_ID));
    }

    private final KurrentDBClient eventStore;
    private final MessageContext context;
    private final MessageSerializer serializer;
    private final ObservationRegistry observations;
    private final SubscriptionCheckpoints checkpoints;
    private final BackOff backOff;
    private final Set<String> subscriptionNames = ConcurrentHashMap.newKeySet();
    private final List<KurrentDBSubscriptionToAll> subscriptions = new CopyOnWriteArrayList<>();
    private final SubscriptionsCatchUp catchUp = new SubscriptionsCatchUp();
    private final Queue<AppendedRange> appendedRanges = new ConcurrentLinkedQueue<>();

    private record AppendedRange(String streamId, long firstRevision, int count) {}

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "kurrentdb-resubscribe");
        thread.setDaemon(true);
        return thread;
    });

    private final boolean ownsClient;

    public KurrentDBEventStore(
            KurrentDBClient kurrentDBClient,
            MessageContext context,
            MessageSerializer serializer,
            ObservationRegistry observations,
            SubscriptionCheckpoints checkpoints,
            BackOff backOff) {
        this(kurrentDBClient, false, context, serializer, observations, checkpoints, backOff);
    }

    public static KurrentDBEventStore connectingTo(
            String connectionString,
            MessageContext context,
            MessageSerializer serializer,
            ObservationRegistry observations,
            SubscriptionCheckpoints checkpoints,
            BackOff backOff) {
        return new KurrentDBEventStore(
                KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(connectionString)),
                true,
                context,
                serializer,
                observations,
                checkpoints,
                backOff);
    }

    private KurrentDBEventStore(
            KurrentDBClient kurrentDBClient,
            boolean ownsClient,
            MessageContext context,
            MessageSerializer serializer,
            ObservationRegistry observations,
            SubscriptionCheckpoints checkpoints,
            BackOff backOff) {
        this.eventStore = kurrentDBClient;
        this.ownsClient = ownsClient;
        this.context = context;
        this.serializer = serializer;
        this.observations = observations;
        this.checkpoints = checkpoints;
        this.backOff = backOff;
    }

    @Override
    public void whenCaughtUp(Duration timeout) {
        for (var range = appendedRanges.poll(); range != null; range = appendedRanges.poll()) {
            for (var resolvedEvent : appendedEventsOf(range)) {
                var recorded = resolvedEvent.getEvent();
                catchUp.appended(recorded.getEventType(), checkpointOf(recorded.getPosition()));
            }
        }

        catchUp.whenCaughtUp(timeout);
    }

    private List<ResolvedEvent> appendedEventsOf(AppendedRange range) {
        try {
            return eventStore
                    .readStream(
                            range.streamId(),
                            ReadStreamOptions.get()
                                    .fromRevision(range.firstRevision())
                                    .maxCount(range.count()))
                    .get()
                    .getEvents();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Reading the events appended to stream %s failed".formatted(range.streamId()), e);
        }
    }
}
