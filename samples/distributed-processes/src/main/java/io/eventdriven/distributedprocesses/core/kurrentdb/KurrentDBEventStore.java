package io.eventdriven.distributedprocesses.core.kurrentdb;

import io.eventdriven.distributedprocesses.core.kurrentdb.subscriptions.AllStreamSubscription;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.kurrent.dbclient.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.backoff.BackOff;

public class KurrentDBEventStore implements EventStore, AutoCloseable {
    @Override
    public ReadResult read(String streamId) {
        try {
            var result =
                    eventStore.readStream(streamId, ReadStreamOptions.get()).get();

            var events = result.getEvents().stream()
                    .map(resolvedEvent -> EventSerializer.deserialize(resolvedEvent)
                            .map(event -> new EventEnvelope<>(event, metadataOf(resolvedEvent))))
                    .flatMap(Optional::stream)
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
        return append(
                streamId,
                StreamState.noStream(),
                events,
                wrongExpectedVersion -> new AppendResult.StreamAlreadyExists(wrongExpectedVersion.getActualState()));
    }

    @Override
    public AppendResult append(String streamId, StreamState expectedRevision, Object... events) {
        return append(
                streamId,
                expectedRevision,
                events,
                wrongExpectedVersion ->
                        new AppendResult.Conflict(expectedRevision, wrongExpectedVersion.getActualState()));
    }

    private AppendResult append(
            String streamId,
            StreamState expectedRevision,
            Object[] events,
            Function<WrongExpectedVersionException, AppendResult> onWrongExpectedVersion) {
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
                            AppendToStreamOptions.get().streamState(expectedRevision),
                            eventsToAppend.iterator())
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            observation.error(e);

            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersion) {
                return onWrongExpectedVersion.apply(wrongExpectedVersion);
            }
            return new AppendResult.UnexpectedFailure(e);
        } finally {
            observation.stop();
        }
    }

    @Override
    public MessageContext.Scope continueTraceOf(String stepName, EventMetadata lastEvent) {
        return context.continuing(stepName, lastEvent);
    }

    @Override
    public EventStore subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        var typeNames = types.stream().map(EventTypeMapper::toName).collect(Collectors.joining("|"));

        var filter = SubscriptionFilter.newBuilder()
                .withEventTypeRegularExpression("^(%s)$".formatted(typeNames))
                .build();

        var subscription = new AllStreamSubscription(
                eventStore,
                name,
                () -> optionsAfterCheckpointOf(name).filter(filter),
                resolvedEvent -> EventSerializer.deserialize(resolvedEvent).ifPresent(event -> {
                    var metadata = metadataOf(resolvedEvent);

                    try (var _ = context.handling(
                            "handle %s".formatted(event.getClass().getSimpleName()), metadata)) {
                        checkpoints.handle(
                                name,
                                metadata.logPosition(),
                                () -> handler.accept(new EventEnvelope<>(event, metadata)));
                    }
                }),
                backOff,
                scheduler);
        subscriptions.add(subscription);
        subscription.start();

        return this;
    }

    public DeleteResult deleteStream(String streamId) {
        try {
            eventStore
                    .deleteStream(streamId, DeleteStreamOptions.get().streamState(StreamState.streamExists()))
                    .get();

            return new DeleteResult.Success();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException) {
                return new DeleteResult.StreamDoesNotExist();
            }
            return new DeleteResult.UnexpectedFailure(e);
        }
    }

    public DeleteResult deleteStream(String streamId, StreamState expectedRevision) {
        try {
            eventStore
                    .deleteStream(streamId, DeleteStreamOptions.get().streamState(expectedRevision))
                    .get();

            return new DeleteResult.Success();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException) {
                return new DeleteResult.StreamDoesNotExist();
            }
            return new DeleteResult.UnexpectedFailure(e);
        }
    }

    public AppendResult setStreamMaxAge(String streamId, Duration duration) {
        try {
            var metadata = new StreamMetadata();
            metadata.setMaxAge(duration.toSeconds());

            var result = eventStore
                    .setStreamMetadata(
                            streamId, AppendToStreamOptions.get().streamState(StreamState.noStream()), metadata)
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
                return new AppendResult.StreamAlreadyExists(wrongExpectedVersionException.getActualState());
            }

            return new AppendResult.UnexpectedFailure(e);
        }
    }

    @Override
    public void close() {
        subscriptions.forEach(AllStreamSubscription::close);
        scheduler.shutdownNow();
        if (ownsClient) eventStore.shutdown();
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
                .map(event -> EventSerializer.serialize(
                        event, values.withMessageId(UUID.randomUUID().toString())))
                .toList();
    }

    private static EventMetadata metadataOf(ResolvedEvent resolvedEvent) {
        var recorded = resolvedEvent.getEvent();
        var userMetadata = EventSerializer.userMetadataOf(resolvedEvent);
        var messageId = recorded.getEventId().toString();
        var correlationId = userMetadata.getOrDefault(EventSerializer.CORRELATION_ID, messageId);

        return new EventMetadata(
                recorded.getStreamId(),
                recorded.getRevision(),
                recorded.getPosition().getCommitUnsigned(),
                messageId,
                correlationId,
                userMetadata.getOrDefault(EventSerializer.CAUSATION_ID, correlationId),
                userMetadata.get(EventSerializer.TRACE_ID),
                userMetadata.get(EventSerializer.SPAN_ID));
    }

    private final KurrentDBClient eventStore;
    private final MessageContext context;
    private final ObservationRegistry observations;
    private final ProcessorCheckpoints checkpoints;
    private final BackOff backOff;
    private final Set<String> subscriptionNames = ConcurrentHashMap.newKeySet();
    private final List<AllStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "kurrentdb-resubscribe");
        thread.setDaemon(true);
        return thread;
    });

    private final boolean ownsClient;

    public KurrentDBEventStore(
            KurrentDBClient kurrentDBClient,
            MessageContext context,
            ObservationRegistry observations,
            ProcessorCheckpoints checkpoints,
            BackOff backOff) {
        this(kurrentDBClient, false, context, observations, checkpoints, backOff);
    }

    public static KurrentDBEventStore connectingTo(
            String connectionString,
            MessageContext context,
            ObservationRegistry observations,
            ProcessorCheckpoints checkpoints,
            BackOff backOff) {
        return new KurrentDBEventStore(
                KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(connectionString)),
                true,
                context,
                observations,
                checkpoints,
                backOff);
    }

    private KurrentDBEventStore(
            KurrentDBClient kurrentDBClient,
            boolean ownsClient,
            MessageContext context,
            ObservationRegistry observations,
            ProcessorCheckpoints checkpoints,
            BackOff backOff) {
        this.eventStore = kurrentDBClient;
        this.ownsClient = ownsClient;
        this.context = context;
        this.observations = observations;
        this.checkpoints = checkpoints;
        this.backOff = backOff;
    }
}
