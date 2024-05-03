package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventStoreDB;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.entities.Aggregate;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.entities.EntityNotFoundException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.immutable.FunctionalTools.FoldLeft.foldLeft;

public class EventStore {
  private final EventStoreDBClient eventStore;

  public static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public EventStore(
    EventStoreDBClient eventStore
  ) {
    this.eventStore = eventStore;
  }

  public <State extends Aggregate<Event>, Event> Optional<State> aggregateStream(
    Class<Event> eventClass,
    Supplier<State> getInitial,
    String streamName
  ) {
    return aggregateStream(eventClass,
      (state, event) -> {
        state.evolve(event);
        return state;
      },
      getInitial,
      streamName
    );
  }

  public <State, Event> Optional<State> aggregateStream(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName
  ) {
    try {
      return Optional.of(
        eventStore.readStream(streamName, ReadStreamOptions.get()).get()
          .getEvents().stream()
          .map(EventStore::deserialize)
          .filter(eventClass::isInstance)
          .map(eventClass::cast)
          .collect(foldLeft(getInitial, evolve))
      );
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return Optional.empty();
      }
      throw new RuntimeException(e);
    }
  }


  public <State extends Aggregate> void add(String streamName, State aggregate) {
    add(streamName, aggregate.dequeueUncommittedEvents().toArray());
  }

  public void add(String streamName, Object[] events) {
    try {
      eventStore.appendToStream(
        streamName,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        Arrays.stream(events).map(EventStore::serialize).iterator()
      ).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public <State extends Aggregate<Event>, Event> void getAndUpdate(
    Class<Event> eventClass,
    Supplier<State> getInitial,
    String streamName,
    Consumer<State> handle
  ) {
    getAndUpdate(eventClass,
      (state, event) -> {
        state.evolve(event);
        return state;
      },
      getInitial,
      streamName,
      (state) -> {
        handle.accept(state);

        return state.dequeueUncommittedEvents();
      }
    );
  }

  public <State, Event> void getAndUpdate(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName,
    Function<State, List<Event>> handle
  ) {
    var entity = aggregateStream(eventClass, evolve, getInitial, streamName)
      .orElseThrow(EntityNotFoundException::new);

    var events = handle.apply(entity);

    try {
      eventStore.appendToStream(
        streamName,
        events.stream().map(EventStore::serialize).iterator()
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

//  public ETag handle(
//    UUID id,
//    Command command,
//    ExpectedRevision expectedRevision
//  ) {
//    var streamId = mapToStreamId.apply(id);
//    var entity = get(id);
//
//    if (entity.isEmpty() && !expectedRevision.equals(ExpectedRevision.noStream()))
//      throw new EntityNotFoundException();
//
//    var event = handle.apply(command, entity.orElse(getDefault.get()));
//
//    try {
//      var result = eventStore.appendToStream(
//        streamId,
//        AppendToStreamOptions.get().expectedRevision(expectedRevision),
//        EventSerializer.serialize(event)
//      ).get();
//
//      return toETag(result.getNextExpectedRevision());
//    } catch (Throwable e) {
//      throw new RuntimeException(e);
//    }
//  }
}
