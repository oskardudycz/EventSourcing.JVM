package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventStoreDB;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.functional.FunctionalTools.FoldLeft.foldLeft;

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
    throw new RuntimeException();
  }

  public <State, Event> Optional<State> aggregateStream(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName
  ) {
    throw new RuntimeException();
  }

  public <State extends Aggregate> void add(String streamName, State aggregate) {
    add(streamName, aggregate.dequeueUncommittedEvents().toArray());
  }

  public void add(String streamName, Object[] events) {
    throw new RuntimeException();
  }

  public <State extends Aggregate<Event>, Event> void getAndUpdate(
    Class<Event> eventClass,
    Supplier<State> getInitial,
    String streamName,
    Consumer<State> handle
  ) {
    throw new RuntimeException();
  }

  public <State, Event> void getAndUpdate(
    Class<Event> eventClass,
    BiFunction<State, Event, State> evolve,
    Supplier<State> getInitial,
    String streamName,
    Function<State, List<Event>> handle
  ) {
    throw new RuntimeException();
  }
}
