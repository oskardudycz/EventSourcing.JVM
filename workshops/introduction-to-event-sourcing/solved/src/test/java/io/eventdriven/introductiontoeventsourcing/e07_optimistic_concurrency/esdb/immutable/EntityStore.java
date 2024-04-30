package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.FunctionalTools.FoldLeft.foldLeft;
import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.FunctionalTools.TriConsumer;

public final class EntityStore {
  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public static <Entity, Event> Optional<Entity> get(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> evolve,
    Supplier<Entity> getEmpty,
    String streamName
  ) {
    try {
      return Optional.of(
        eventStore.readStream(streamName, ReadStreamOptions.get()).get()
          .getEvents().stream()
          .map(EntityStore::deserialize)
          .filter(eventClass::isInstance)
          .map(eventClass::cast)
          .collect(foldLeft(getEmpty, evolve))
      );
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return Optional.empty();
      }
      throw new RuntimeException(e);
    }
  }

  public static <Entity, Command, Event> void getAndUpdate(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> evolve,
    Supplier<Entity> getEmpty,
    BiFunction<Command, Entity, Event> handle,
    String streamName,
    Command command,
    Long expectedRevision
  ) {
    get(eventClass, eventStore, evolve, getEmpty, streamName)
      .ifPresentOrElse(
        current -> store(
          eventStore,
          streamName,
          handle.apply(command, current),
          ExpectedRevision.expectedRevision(expectedRevision)
        ),
        () -> store(
          eventStore,
          streamName,
          handle.apply(command, getEmpty.get()),
          ExpectedRevision.noStream()
        )
      );
  }

  public static <Entity, Event, Command> TriConsumer<UUID, Command, Long> commandHandler(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    Supplier<Entity> getEmpty,
    Function<UUID, String> toStreamName,
    BiFunction<Entity, Event, Entity> evolve,
    BiFunction<Command, Entity, Event> handle
  ) {
    return (id, command, expectedRevision) -> {
      var streamName = toStreamName.apply(id);

      EntityStore.getAndUpdate(
        eventClass,
        eventStore,
        evolve,
        getEmpty,
        handle,
        streamName,
        command,
        expectedRevision
      );
    };
  }

  private static void store(EventStoreDBClient eventStore, String streamName, Object event, ExpectedRevision expectedRevision) {
    try {
      eventStore.appendToStream(
        streamName,
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


