package io.eventdriven.ecommerce.core.aggregates;

import com.eventstore.dbclient.*;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

import javax.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AggregateStore<Entity extends AbstractAggregate<Id>, Event, Id> {
  private final EventStoreDBClient eventStore;
  private final BiFunction<Entity, Event, Entity> when;
  private final Function<Id, String> mapToStreamId;

  public AggregateStore(
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> when,
    Function<Id, String> mapToStreamId
  ) {

    this.eventStore = eventStore;
    this.when = when;
    this.mapToStreamId = mapToStreamId;
  }

  Optional<Entity> get(Id id) {
    var streamId = mapToStreamId.apply(id);

    var events = getEvents(streamId);

    if (events.isEmpty())
      return Optional.empty();

    Entity current = null;

    for (var event : events.get()) {
      current = when.apply(current, event);
    }

    return Optional.ofNullable(current);
  }

  public ETag add(
    Supplier<Entity> handle
  ) {
    var entity = handle.get();

    var streamId = mapToStreamId.apply(entity.id());
    var events = Arrays.stream(entity.dequeueUncommittedEvents())
      .map(event -> EventSerializer.serialize(event));

    try {
      var result =
        eventStore.appendToStream(
          streamId,
          AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
          events.iterator()
        ).get();

      return ETag.weak(result.getNextExpectedRevision());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public ETag getAndUpdate(
    Consumer<Entity> handle,
    Id id,
    long expectedRevision
  ) {

    var streamId = mapToStreamId.apply(id);
    var getResult = get(id);

    if (getResult.isEmpty()) {
      throw new EntityNotFoundException("Stream with id %s was not found".formatted(streamId));
    }
    var entity = getResult.get();

    handle.accept(entity);

    var events = Arrays.stream(entity.dequeueUncommittedEvents())
      .map(event -> EventSerializer.serialize(event));

    try {
      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        events.iterator()
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
