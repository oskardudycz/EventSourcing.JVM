package io.eventdriven.distributedprocesses.core.aggregates;

import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.http.ETag;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AggregateStore<Entity extends AbstractAggregate<Event, Id>, Event, Id> {
  private static final int maxAttempts = 3;

  private final EventStore eventStore;
  private final Function<Id, String> mapToStreamId;
  private final Supplier<Entity> getEmpty;

  public AggregateStore(
    EventStore eventStore,
    Function<Id, String> mapToStreamId,
    Supplier<Entity> getEmpty
  ) {

    this.eventStore = eventStore;
    this.mapToStreamId = mapToStreamId;
    this.getEmpty = getEmpty;
  }

  public Optional<Entity> get(Id id) {
    var streamId = mapToStreamId.apply(id);

    return switch (eventStore.read(streamId)) {
      case EventStore.ReadResult.StreamDoesNotExist ignored -> Optional.empty();
      case EventStore.ReadResult.NoEventsFound ignored -> Optional.empty();
      case EventStore.ReadResult.Success success -> Optional.of(replay(success.events()));
      case EventStore.ReadResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
    };
  }

  public ETag getAndUpdate(Id id, Consumer<Entity> handle) {
    for (var attempt = 1; ; attempt++) {
      var entity = get(id).orElseGet(getEmpty);

      try {
        return update(id, entity, entity.version, handle);
      } catch (ConcurrentModificationException e) {
        if (attempt == maxAttempts)
          throw e;
      }
    }
  }

  public ETag getAndUpdate(Id id, long expectedVersion, Consumer<Entity> handle) {
    return update(id, get(id).orElseGet(getEmpty), expectedVersion, handle);
  }

  private ETag update(Id id, Entity entity, long expectedVersion, Consumer<Entity> handle) {
    handle.accept(entity);

    var events = entity.dequeueUncommittedEvents();

    if (events.length == 0)
      return ETag.weak(expectedVersion);

    var streamId = mapToStreamId.apply(id);

    return switch (eventStore.append(streamId, toExpectedRevision(expectedVersion), events)) {
      case EventStore.AppendResult.Success success -> ETag.weak(success.nextExpectedRevision());
      case EventStore.AppendResult.StreamAlreadyExists alreadyExists -> throw new ConcurrentModificationException(
        "Cannot append to stream %s: expected no stream, but it is at %s"
          .formatted(streamId, describe(alreadyExists.actual()))
      );
      case EventStore.AppendResult.Conflict conflict -> throw new ConcurrentModificationException(
        "Cannot append to stream %s: expected %s, but it is at %s"
          .formatted(streamId, describe(conflict.expected()), describe(conflict.actual()))
      );
      case EventStore.AppendResult.UnexpectedFailure failure -> throw new RuntimeException(failure.t());
    };
  }

  private static ExpectedRevision toExpectedRevision(long version) {
    return version < 0 ?
      ExpectedRevision.noStream()
      : ExpectedRevision.expectedRevision(version);
  }

  @SuppressWarnings("unchecked")
  private Entity replay(Object[] events) {
    var current = getEmpty.get();

    for (var event : events) {
      current.evolve((Event) event);
    }

    current.version = events.length - 1;

    return current;
  }

  private static String describe(ExpectedRevision revision) {
    if (revision.equals(ExpectedRevision.noStream()))
      return "no stream";

    if (revision.equals(ExpectedRevision.streamExists()))
      return "stream exists";

    if (revision.equals(ExpectedRevision.any()))
      return "any revision";

    return "revision %s".formatted(revision);
  }
}
