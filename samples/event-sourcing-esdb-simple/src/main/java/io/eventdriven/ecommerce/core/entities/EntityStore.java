package io.eventdriven.ecommerce.core.entities;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityStore<TEntity> {
  private final EventStoreDBClient eventStore;
  private final BiFunction<TEntity, Object, TEntity> when;
  private final Function<UUID, String> mapToStreamId;
  private final Supplier<TEntity> getDefault;

  public EntityStore(
    EventStoreDBClient eventStore,
    BiFunction<TEntity, Object, TEntity> when,
    Function<UUID, String> mapToStreamId,
    Supplier<TEntity> getDefault
  ) {

    this.eventStore = eventStore;
    this.when = when;
    this.mapToStreamId = mapToStreamId;
    this.getDefault = getDefault;
  }

  public TEntity Get(UUID id) throws ExecutionException, InterruptedException {
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

  public CompletableFuture<Void> Add(
    Supplier<Object> handle,
    UUID id
  ) {
    var streamId = mapToStreamId.apply(id);
    var event = handle.get();

    return appendToStream(streamId, event, Optional.empty());
  }

  public CompletableFuture<Void> GetAndUpdate(
    Function<TEntity, Object> handle,
    UUID id,
    Optional<Long> expectedRevision
  ) throws ExecutionException, InterruptedException {

    var streamId = mapToStreamId.apply(id);
    var entity = Get(id);

    var event = handle.apply(entity);

    return appendToStream(streamId, event, expectedRevision);
  }

  private CompletableFuture<Void> appendToStream(
    String streamId,
    Object event,
    Optional<Long> expectedRevision
  ) {
    var appendOptions = AppendToStreamOptions.get();

    if (!expectedRevision.isEmpty())
      appendOptions.expectedRevision(expectedRevision.get());
    else
      appendOptions.expectedRevision(ExpectedRevision.STREAM_EXISTS);

    return CompletableFuture.allOf(
      eventStore.appendToStream(
        streamId,
        appendOptions,
        EventSerializer.Serialize(event)
      )
    );
  }
}
