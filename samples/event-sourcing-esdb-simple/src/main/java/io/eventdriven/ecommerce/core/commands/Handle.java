package io.eventdriven.ecommerce.core.commands;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.ecommerce.core.events.EventTypeMapper;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Handle {

  public static <TEntity> TEntity Get(
    EventStoreDBClient eventStore,
    Supplier<TEntity> getDefault,
    BiFunction<TEntity, Object, TEntity> when,
    String streamId
  ) throws ExecutionException, InterruptedException {
    var current = getDefault.get();

    var result = eventStore.readStream(streamId).get();

    var events = result.getEvents().stream()
      .map(e -> EventSerializer.Deserialize(e))
      .toList();

    for (var event : events) {
      current = when.apply(current, event);
    }

    return current;
  }

  public static <TCommand> CompletableFuture<Void> Add(
    EventStoreDBClient eventStore,
    Function<TCommand, Object> handle,
    String streamId,
    TCommand command
  ) {
    var event = handle.apply(command);

    var appendOptions = AppendToStreamOptions.get()
      .expectedRevision(ExpectedRevision.NO_STREAM);

    return CompletableFuture.allOf(eventStore.appendToStream(
      streamId,
      appendOptions,
      EventData.builderAsJson(
        UUID.randomUUID(),
        EventTypeMapper.ToName(event.getClass()),
        event
      ).build()
    ));
  }

  public static <TEntity, TCommand> CompletableFuture<Void> GetAndUpdate(
    EventStoreDBClient eventStore,
    Supplier<TEntity> getDefault,
    BiFunction<TEntity, Object, TEntity> when,
    BiFunction<TEntity, TCommand, Object> handle,
    String streamId,
    TCommand command,
    Optional<Long> expectedRevision
  ) throws ExecutionException, InterruptedException {

    var entity = Get(eventStore, getDefault, when, streamId);

    var event = handle.apply(entity, command);

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
