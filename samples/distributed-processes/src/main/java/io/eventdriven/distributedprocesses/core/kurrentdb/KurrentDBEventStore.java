package io.eventdriven.distributedprocesses.core.kurrentdb;

import io.eventdriven.distributedprocesses.core.kurrentdb.subscriptions.KurrentDBSubscription;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.kurrent.dbclient.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class KurrentDBEventStore implements EventStore {
    @Override
    public ReadResult read(String streamId) {
        try {
            var result =
                    eventStore.readStream(streamId, ReadStreamOptions.get()).get();

            var events = result.getEvents().stream()
                    .map(resolvedEvent -> EventSerializer.deserialize(resolvedEvent))
                    .flatMap(Optional::stream)
                    .toArray();

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
        var eventsToAppend =
                Arrays.stream(events).map(EventSerializer::serialize).toList();

        try {
            var result = eventStore
                    .appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(StreamState.noStream()),
                            eventsToAppend.iterator())
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
    public AppendResult append(String streamId, StreamState expectedRevision, Object... events) {
        try {
            var eventsToAppend =
                    Arrays.stream(events).map(EventSerializer::serialize).toList();

            var result = eventStore
                    .appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(expectedRevision),
                            eventsToAppend.iterator())
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
                return new AppendResult.Conflict(expectedRevision, wrongExpectedVersionException.getActualState());
            }
            return new AppendResult.UnexpectedFailure(e);
        }
    }

    @Override
    public <Event> EventStore subscribe(Class<Event> type, Consumer<EventEnvelope<Event>> handler) {
        var options = SubscribeToAllOptions.get()
                .fromStart()
                .filter(SubscriptionFilter.newBuilder()
                        .withEventTypeRegularExpression("^%s$".formatted(EventTypeMapper.toName(type)))
                        .build());

        KurrentDBSubscription.subscribeToAll(eventStore, options, (subscription, resolvedEvent) -> {
            var recorded = resolvedEvent.getEvent();

            EventSerializer.deserialize(type, resolvedEvent)
                    .ifPresent(event -> handler.accept(new EventEnvelope<>(
                            event,
                            new EventMetadata(
                                    recorded.getStreamId(),
                                    recorded.getRevision(),
                                    recorded.getPosition().getCommitUnsigned()))));
        });

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

    private final KurrentDBClient eventStore;

    public KurrentDBEventStore(KurrentDBClient kurrentDBClient) {
        this.eventStore = kurrentDBClient;
    }
}
