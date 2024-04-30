package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed.BusinessLogic.Aggregate;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityStore<Entity extends Aggregate<Event>, Event, Command> {
  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  private final Class<Entity> entityClass;
  private final Class<Event> eventClass;
  private final EventStoreDBClient eventStore;
  private final Supplier<Entity> getEmpty;
  private final Function<UUID, String> toStreamId;

  public EntityStore(
    Class<Entity> entityClass,
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    Supplier<Entity> getEmpty,
    Function<UUID, String> toStreamId
  ){
    this.entityClass = entityClass;
    this.eventClass = eventClass;
    this.eventStore = eventStore;
    this.getEmpty = getEmpty;
    this.toStreamId = toStreamId;
  }

  public Entity get(UUID id) {
    try {
      var events = eventStore.readStream(toStreamId.apply(id), ReadStreamOptions.get()).get()
        .getEvents().stream()
        .map(EntityStore::deserialize)
        .filter(eventClass::isInstance)
        .map(eventClass::cast)
        .toList();

      var entity = getEmpty.get();

      for (var event : events) {
        entity.evolve(event);
      }

      return entity;
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public <C extends Command> void add(UUID id, Event event) {
    store(id, event, ExpectedRevision.noStream());
  }

  public <C extends Command> void getAndUpdate(
    UUID id,
    C command,
    Long expectedVersion,
    BiFunction<C, Entity, Event> commandHandler
  ) {
    var entity = get(id);

    var event = commandHandler.apply(command, entity);

    store(id, event, ExpectedRevision.expectedRevision(expectedVersion));
  }

  private void store(UUID id, Event event, ExpectedRevision expectedRevision){
    try {
      eventStore.appendToStream(
        toStreamId.apply(id),
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EntityStore.serialize(event)
      ).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        event.getClass().getTypeName(),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
