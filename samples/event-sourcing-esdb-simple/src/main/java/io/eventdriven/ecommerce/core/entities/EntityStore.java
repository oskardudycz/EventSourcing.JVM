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

public class EntityStore<Entity> {
  private final EventStoreDBClient eventStore;
  private final BiFunction<Entity, Object, Entity> when;
  private final Function<UUID, String> mapToStreamId;
  private final Supplier<Entity> getDefault;

  public EntityStore(
    EventStoreDBClient eventStore,
    BiFunction<Entity, Object, Entity> when,
    Function<UUID, String> mapToStreamId,
    Supplier<Entity> getDefault
  ) {

    this.eventStore = eventStore;
    this.when = when;
    this.mapToStreamId = mapToStreamId;
    this.getDefault = getDefault;
  }

  public Entity Get(UUID id) throws ExecutionException, InterruptedException {
    var streamId = mapToStreamId.apply(id);
    var result = eventStore.readStream(streamId).get();

    var events = result.getEvents().stream()
      .map(e -> EventSerializer.Deserialize(e))
      .toList();

    var current = getDefault.get();
    for (var event : events) {
      current = when.apply(current, event);
    }

    return current;
  }

  public ETag Add(
    Supplier<Object> handle,
    UUID id
  ) throws ExecutionException, InterruptedException {
    var streamId = mapToStreamId.apply(id);
    var event = handle.get();

    return appendToStream(streamId, event, Optional.empty());
  }

  public ETag GetAndUpdate(
    Function<Entity, Object> handle,
    UUID id,
    Optional<Long> expectedRevision
  ) throws ExecutionException, InterruptedException {

    var streamId = mapToStreamId.apply(id);
    var entity = Get(id);

    var event = handle.apply(entity);

    return appendToStream(streamId, event, expectedRevision);
  }

  private ETag appendToStream(
    String streamId,
    Object event,
    Optional<Long> expectedRevision
  ) throws ExecutionException, InterruptedException {
    var appendOptions = AppendToStreamOptions.get();

    if (!expectedRevision.isEmpty())
      appendOptions.expectedRevision(expectedRevision.get());
    else
      appendOptions.expectedRevision(ExpectedRevision.NO_STREAM);

    var result =
      eventStore.appendToStream(
        streamId,
        appendOptions,
        EventSerializer.Serialize(event)
      ).get();

    return ETag.weak(result.getNextExpectedRevision());
  }
}
