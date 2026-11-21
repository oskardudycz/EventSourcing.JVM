package io.eventdriven.distributedprocesses.core.aggregates;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.kurrent.dbclient.StreamState;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AggregateStore<Entity extends AbstractAggregate<Event, Id>, Event, Id> {
    private static final int maxAttempts = 3;

    private final EventStore eventStore;
    private final Function<Id, String> mapToStreamId;
    private final Supplier<Entity> getEmpty;

    public AggregateStore(EventStore eventStore, Function<Id, String> mapToStreamId, Supplier<Entity> getEmpty) {

        this.eventStore = eventStore;
        this.mapToStreamId = mapToStreamId;
        this.getEmpty = getEmpty;
    }

    public Optional<Entity> get(Id id) {
        var events = eventsOf(id);

        return events.isEmpty() ? Optional.empty() : Optional.of(replay(events));
    }

    public ETag getAndUpdate(Id id, Consumer<Entity> handle) {
        return getAndUpdate(id, handle, ignored -> () -> {});
    }

    public ETag getAndUpdateContinuingTrace(Id id, Object command, Consumer<Entity> handle) {
        var stepName = "handle %s".formatted(command.getClass().getSimpleName());

        return getAndUpdate(
                id,
                handle,
                events -> events.isEmpty()
                        ? () -> {}
                        : eventStore.continueTraceOf(stepName, events.getLast().metadata()));
    }

    public ETag getAndUpdate(Id id, long expectedVersion, Consumer<Entity> handle) {
        return update(id, get(id).orElseGet(getEmpty), expectedVersion, handle);
    }

    private ETag update(Id id, Entity entity, long expectedVersion, Consumer<Entity> handle) {
        handle.accept(entity);

        var events = entity.dequeueUncommittedEvents();

        if (events.length == 0) return ETag.weak(expectedVersion);

        var streamId = mapToStreamId.apply(id);

        return switch (eventStore.append(streamId, toExpectedRevision(expectedVersion), events)) {
            case EventStore.AppendResult.Success success -> ETag.weak(success.nextExpectedRevision());
            case EventStore.AppendResult.StreamAlreadyExists alreadyExists ->
                throw new ConcurrentModificationException(
                        "Cannot append to stream %s: expected no stream, but it is at %s"
                                .formatted(streamId, describe(alreadyExists.actual())));
            case EventStore.AppendResult.Conflict conflict ->
                throw new ConcurrentModificationException("Cannot append to stream %s: expected %s, but it is at %s"
                        .formatted(streamId, describe(conflict.expected()), describe(conflict.actual())));
            case EventStore.AppendResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
        };
    }

    private static StreamState toExpectedRevision(long version) {
        return version < 0 ? StreamState.noStream() : StreamState.streamRevision(version);
    }

    private ETag getAndUpdate(
            Id id, Consumer<Entity> handle, Function<List<EventEnvelope<Object>>, MessageContext.Scope> scopeOf) {
        for (var attempt = 1; ; attempt++) {
            var events = eventsOf(id);
            var entity = events.isEmpty() ? getEmpty.get() : replay(events);

            try (var _ = scopeOf.apply(events)) {
                return update(id, entity, entity.version, handle);
            } catch (ConcurrentModificationException e) {
                if (attempt == maxAttempts) throw e;
            }
        }
    }

    private List<EventEnvelope<Object>> eventsOf(Id id) {
        return switch (eventStore.read(mapToStreamId.apply(id))) {
            case EventStore.ReadResult.StreamDoesNotExist ignored -> List.of();
            case EventStore.ReadResult.NoEventsFound ignored -> List.of();
            case EventStore.ReadResult.Success success -> success.events();
            case EventStore.ReadResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
        };
    }

    @SuppressWarnings("unchecked")
    private Entity replay(List<EventEnvelope<Object>> events) {
        var current = getEmpty.get();

        for (var envelope : events) {
            current.evolve((Event) envelope.data());
        }

        current.version = events.size() - 1;

        return current;
    }

    private static String describe(StreamState revision) {
        if (revision.equals(StreamState.noStream())) return "no stream";

        if (revision.equals(StreamState.streamExists())) return "stream exists";

        if (revision.equals(StreamState.any())) return "any revision";

        return "revision %s".formatted(revision);
    }
}
