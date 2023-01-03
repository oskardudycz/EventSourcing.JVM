package io.eventdriven.ecommerce.core.entities;

import com.eventstore.dbclient.*;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class CommandHandler<Entity, Command, Event> {
  private final EventStoreDBClient eventStore;
  private final BiFunction<Entity, Event, Entity> when;
  private final Function<UUID, String> mapToStreamId;
  private final Supplier<Entity> getDefault;
  private final BiFunction<Command, Entity, Event> handle;

  public CommandHandler(
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> when,
    BiFunction<Command, Entity, Event> handle,
    Function<UUID, String> mapToStreamId,
    Supplier<Entity> getDefault
  ) {

    this.eventStore = eventStore;
    this.when = when;
    this.handle = handle;
    this.mapToStreamId = mapToStreamId;
    this.getDefault = getDefault;
  }

  public ETag handle(
    UUID id,
    Command command,
    ExpectedRevision expectedRevision
  ) {
    var streamId = mapToStreamId.apply(id);
    var entity = get(id);

    if(entity.isEmpty() && !expectedRevision.equals(ExpectedRevision.noStream()))
      throw new EntityNotFoundException();

    var event = handle.apply(command, entity.orElse(getDefault.get()));

    try {
      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(event)
      ).get();

      return toETag(result.getNextExpectedRevision());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<Entity> get(UUID id) {
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

  private Optional<List<Event>> getEvents(String streamId) {
    ReadResult result;
    try {
      result = eventStore.readStream(streamId, ReadStreamOptions.get()).get();
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

  //This ugly hack is needed as ESDB Java client from v4 doesn't allow to access or serialise version in an elegant manner
  private ETag toETag(ExpectedRevision nextExpectedRevision) throws NoSuchFieldException, IllegalAccessException {
    var field = nextExpectedRevision.getClass().getDeclaredField("version");
    field.setAccessible(true);

    return ETag.weak(field.get(nextExpectedRevision));
  }
}
