package io.eventdriven.core.aggregates;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.http.ETag;
import io.eventdriven.core.messaging.EventEnvelope;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public class AggregateStore<Entity extends AbstractAggregate<Event, Id>, Event, Id> {
    private static final RetryTemplate retry = new RetryTemplate(RetryPolicy.builder()
            .includes(ConcurrentModificationException.class)
            .maxRetries(2)
            .delay(Duration.ZERO)
            .build());

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
        return retry.invoke(() -> {
            var events = eventsOf(id);
            var entity = events.isEmpty() ? getEmpty.get() : replay(events);

            return update(id, entity, toExpectedVersion(entity.version), handle);
        });
    }

    public ETag getAndUpdate(Id id, ExpectedStreamVersion expectedVersion, Consumer<Entity> handle) {
        return update(id, get(id).orElseGet(getEmpty), expectedVersion, handle);
    }

    private ETag update(Id id, Entity entity, ExpectedStreamVersion expectedVersion, Consumer<Entity> handle) {
        handle.accept(entity);

        var events = entity.dequeueUncommittedEvents();

        if (events.length == 0)
            return ETag.weak(expectedVersion instanceof ExpectedStreamVersion.At at ? at.version() : -1);

        var streamId = mapToStreamId.apply(id);

        return switch (eventStore.append(streamId, expectedVersion, events)) {
            case EventStore.AppendResult.Success success -> ETag.weak(success.nextStreamVersion());
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

    private static ExpectedStreamVersion toExpectedVersion(long version) {
        return version < 0 ? ExpectedStreamVersion.noStream() : ExpectedStreamVersion.at(version);
    }

    private List<EventEnvelope<Object>> eventsOf(Id id) {
        return switch (eventStore.read(mapToStreamId.apply(id))) {
            case EventStore.ReadResult.StreamDoesNotExist ignored -> List.of();
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

    private static String describe(long version) {
        return version < 0 ? "no stream" : "version %s".formatted(version);
    }

    private static String describe(ExpectedStreamVersion expected) {
        return switch (expected) {
            case ExpectedStreamVersion.NoStream ignored -> "no stream";
            case ExpectedStreamVersion.StreamExists ignored -> "stream exists";
            case ExpectedStreamVersion.Any ignored -> "any version";
            case ExpectedStreamVersion.At at -> describe(at.version());
        };
    }
}
