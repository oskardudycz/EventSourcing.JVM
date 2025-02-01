package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.eventStoreDB;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.entities.Aggregate;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.entities.EntityNotFoundException;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.functional.Tuple;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.functional.FunctionalTools.FoldLeft.foldLeft;

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

  public <State extends Aggregate<Event>, Event> Optional<Tuple<State, Long>> aggregateStream(
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

  public <State, Event> Optional<Tuple<State, Long>> aggregateStream(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName
  ) {
    try {
      AtomicReference<Long> position = new AtomicReference<>(null);

      var result = eventStore.readStream(streamName, ReadStreamOptions.get()).get()
        .getEvents().stream()
        .map(resolvedEvent -> {
          position.set(resolvedEvent.getEvent().getRevision());
          return deserialize(resolvedEvent);
        })
        .filter(eventClass::isInstance)
        .map(eventClass::cast)
        .collect(foldLeft(getInitial, evolve));

      if (result == null)
        return Optional.empty();

      return Optional.of(new Tuple<>(result, position.get()));
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return Optional.empty();
      }
      throw new RuntimeException(e);
    }
  }


  public <State extends Aggregate> long add(String streamName, State aggregate) {
    return add(streamName, aggregate.dequeueUncommittedEvents().toArray());
  }

  public long add(String streamName, Object[] events) {
    try {
      var result = eventStore.appendToStream(
        streamName,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        Arrays.stream(events).map(this::serialize).iterator()
      ).get();

      return result.getNextExpectedRevision().toRawLong();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public <State extends Aggregate<Event>, Event> long getAndUpdate(
    Class<Event> eventClass,
    Supplier<State> getInitial,
    String streamName,
    Long expectedRevision,
    Consumer<State> handle
  ) {
    return getAndUpdate(eventClass,
      (state, event) -> {
        state.evolve(event);
        return state;
      },
      getInitial,
      streamName,
      expectedRevision,
      (state) -> {
        handle.accept(state);

        return state.dequeueUncommittedEvents();
      }
    );
  }

  public <State, Event> long getAndUpdate(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName,
    Long expectedRevision,
    Function<State, List<Event>> handle
  ) {
    var current = aggregateStream(eventClass, evolve, getInitial, streamName)
      .orElseThrow(EntityNotFoundException::new);

    var entity = current.first();
    var currentRevision = current.second();

    var events = handle.apply(entity);

    var options = AppendToStreamOptions.get()
      .expectedRevision(expectedRevision != null ? expectedRevision : currentRevision);

    try {
      var result = eventStore.appendToStream(
        streamName,
        options,
        events.stream().map(this::serialize).iterator()
      ).get();

      return result.getNextExpectedRevision().toRawLong();
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
