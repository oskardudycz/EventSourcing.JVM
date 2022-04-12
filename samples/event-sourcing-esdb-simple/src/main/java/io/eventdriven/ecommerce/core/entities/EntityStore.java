package io.eventdriven.ecommerce.core.entities;

import com.eventstore.dbclient.*;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityStore<Entity, Event> {
  private final EventStoreDBClient eventStore;
  private final BiFunction<Entity, Event, Entity> when;
  private final Function<UUID, String> mapToStreamId;
  private final Supplier<Entity> getDefault;

  public EntityStore(
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> when,
    Function<UUID, String> mapToStreamId,
    Supplier<Entity> getDefault
  ) {

    this.eventStore = eventStore;
    this.when = when;
    this.mapToStreamId = mapToStreamId;
    this.getDefault = getDefault;
  }

  Optional<Entity> get(UUID id) {
    var streamId = mapToStreamId.apply(id);

    var events = getEvents(streamId);

    if (events.isEmpty())
      return Optional.empty();

    var current = getDefault.get();

    for (var event : events.get()) {
      current = when.apply(current, event);
    }

    return Optional.of(current);
  }

  public ETag add(
    Supplier<Object> handle,
    UUID id
  ) {
    var streamId = mapToStreamId.apply(id);
    var event = handle.get();

    try {
      var result =
        eventStore.appendToStream(
          streamId,
          AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
          EventSerializer.serialize(event)
        ).get();

      return ETag.weak(result.getNextExpectedRevision());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public ETag getAndUpdate(
    Function<Entity, Event> handle,
    UUID id,
    long expectedRevision
  ) {

    var streamId = mapToStreamId.apply(id);
    var entity = get(id).orElseThrow(
      () -> new EntityNotFoundException("Stream with id %s was not found".formatted(streamId))
    );

    var event = handle.apply(entity);

    try {
      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(event)
      ).get();

      return ETag.weak(result.getNextExpectedRevision());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<List<Event>> getEvents(String streamId) {
    ReadResult result;
    try {
      result = eventStore.readStream(streamId).get();
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return Optional.empty();
      }
      throw new RuntimeException(e);
    }

    var events = result.getEvents().stream()
      .map(EventSerializer::<Event>deserialize)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .toList();

    return Optional.of(events);
  }
}
