package io.eventdriven.distributedprocesses.core.entities;

import com.eventstore.dbclient.*;
import io.eventdriven.distributedprocesses.core.entities.EntityStore.GetEntityResult.Success;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.util.Pair;

public class EntityStore<Entity, Event> {
  private final EventStoreDBClient eventStore;
  private final Function<UUID, String> mapToStreamId;
  private final BiFunction<Entity, Event, Entity> evolve;
  private final Supplier<Entity> getEmpty;

  public EntityStore(
      EventStoreDBClient eventStore,
      Function<UUID, String> mapToStreamId,
      BiFunction<Entity, Event, Entity> evolve,
      Supplier<Entity> getEmpty) {

    this.eventStore = eventStore;
    this.mapToStreamId = mapToStreamId;
    this.evolve = evolve;
    this.getEmpty = getEmpty;
  }

  public Optional<ETag> getAndUpdate(Function<Entity, Event[]> handle, UUID id) {
    return getAndUpdate(handle, id, null);
  }

  public Optional<ETag> getAndUpdate(Function<Entity, Event[]> handle, UUID id, ETag eTag) {
    var streamId = mapToStreamId.apply(id);
    var getResult = get(streamId);

    var state = getResult instanceof Success<Entity> success ? success.result : getEmpty.get();
    var expectedRevision = eTag != null
        ? ExpectedRevision.expectedRevision(eTag.toLong())
        : getResult instanceof Success<Entity> success
            ? ExpectedRevision.expectedRevision(success.revision)
            : ExpectedRevision.noStream();

    var events = handle.apply(state);

    if (events.length == 0) return Optional.empty();

    return Optional.of(appendEvents(streamId, events, expectedRevision));
  }

  private GetEntityResult<Entity> get(String streamId) {
    var result = getEvents(streamId);
    var events = result.getFirst();

    if (events.isEmpty()) return GetEntityResult.notFound();

    var current = getEmpty.get();

    for (var event : events.get()) {
      current = evolve.apply(current, event);
    }

    var lastEventRevision = result.getSecond();

    return GetEntityResult.success(current, lastEventRevision.get());
  }

  private Pair<Optional<List<Event>>, Optional<Long>> getEvents(String streamId) {
    ReadResult result;
    try {
      result = eventStore.readStream(streamId, ReadStreamOptions.get()).get();
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return Pair.of(Optional.empty(), Optional.empty());
      }
      throw new RuntimeException(e);
    }

    var resultEvents = result.getEvents().stream();

    var events = resultEvents
        .map(EventSerializer::<Event>deserialize)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();

    var lastEventRevision = resultEvents
        .reduce((first, second) -> second)
        .map(e -> e.getOriginalEvent().getRevision());

    return Pair.of(Optional.of(events), lastEventRevision);
  }

  public ETag appendEvents(String streamId, Event[] events, ExpectedRevision expectedRevision) {
    var eventsToAppend = Arrays.stream(events).map(EventSerializer::serialize);

    try {
      var result = eventStore
          .appendToStream(
              streamId,
              AppendToStreamOptions.get().expectedRevision(expectedRevision),
              eventsToAppend.iterator())
          .get();

      return ETag.weak(result.getNextExpectedRevision());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  sealed interface GetEntityResult<Entity> {
    record NotFound<Entity>() implements GetEntityResult<Entity> {}

    record Success<Entity>(Entity result, long revision) implements GetEntityResult<Entity> {}

    static <Entity> NotFound<Entity> notFound() {
      return new NotFound();
    }

    static <Entity> Success<Entity> success(Entity result, long revision) {
      return new Success(result, revision);
    }
  }
}
