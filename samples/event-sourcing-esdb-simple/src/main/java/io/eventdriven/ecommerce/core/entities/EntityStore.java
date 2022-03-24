package io.eventdriven.ecommerce.core.entities;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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

  Entity get(UUID id) throws ExecutionException, InterruptedException {
    var streamId = mapToStreamId.apply(id);
    var result = eventStore.readStream(streamId).get();

    var events = result.getEvents().stream()
      .map(EventSerializer::<Event>deserialize)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .toList();

    var current = getDefault.get();

    for (var event : events) {
      current = when.apply(current, event);
    }

    return current;
  }

  public ETag add(
    Supplier<Object> handle,
    UUID id
  ) throws ExecutionException, InterruptedException {
    var streamId = mapToStreamId.apply(id);
    var event = handle.get();

    var result =
      eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        EventSerializer.serialize(event)
      ).get();

    return ETag.weak(result.getNextExpectedRevision());
  }

  public ETag getAndUpdate(
    Function<Entity, Event> handle,
    UUID id,
    long expectedRevision
  ) throws ExecutionException, InterruptedException {

    var streamId = mapToStreamId.apply(id);
    var entity = get(id);

    var event = handle.apply(entity);

    var result =
      eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(event)
      ).get();

    return ETag.weak(result.getNextExpectedRevision());
  }
}
