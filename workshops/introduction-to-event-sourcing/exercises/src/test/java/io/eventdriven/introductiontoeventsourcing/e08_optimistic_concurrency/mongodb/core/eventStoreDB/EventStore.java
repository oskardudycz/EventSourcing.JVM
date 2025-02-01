package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.eventStoreDB;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.entities.Aggregate;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.entities.EntityNotFoundException;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.functional.FunctionalTools;

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

public class EventStore {
  private final EventStoreDBClient eventStore;
  private final ObjectMapper mapper;

  public EventStore(
    EventStoreDBClient eventStore,
    ObjectMapper mapper
  ) {
    this.eventStore = eventStore;
    this.mapper = mapper;
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
          .map(this::deserialize)
          .filter(eventClass::isInstance)
          .map(eventClass::cast)
          .collect(FunctionalTools.FoldLeft.foldLeft(getInitial, evolve))
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
        Arrays.stream(events).map(this::serialize).iterator()
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
        events.stream().map(this::serialize).iterator()
      ).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private EventData serialize(Object event) {
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

  private Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
