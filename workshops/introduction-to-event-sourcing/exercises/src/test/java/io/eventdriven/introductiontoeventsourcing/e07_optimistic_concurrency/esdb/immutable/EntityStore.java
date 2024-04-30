package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable;

import com.eventstore.dbclient.EventStoreDBClient;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.immutable.FunctionalTools.*;

public final class EntityStore {
  public static <Entity, Event> Optional<Entity> get(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> evolve,
    Supplier<Entity> getEmpty,
    String streamName
  ) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public static <Entity, Command, Event> void getAndUpdate(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    BiFunction<Entity, Event, Entity> evolve,
    Supplier<Entity> getEmpty,
    BiFunction<Command, Entity, Event> handle,
    String streamName,
    Command command,
    long expectedRevision
  ) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
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
}


