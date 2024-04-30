package io.eventdriven.introductiontoeventsourcing.e06_business_logic.esdb.immutable;

import com.eventstore.dbclient.EventStoreDBClient;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

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
    Command command
  ) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public static <Entity, Event, Command> BiConsumer<UUID, Command> commandHandler(
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    Supplier<Entity> getEmpty,
    Function<UUID, String> toStreamName,
    BiFunction<Entity, Event, Entity> evolve,
    BiFunction<Command, Entity, Event> handle
  ) {
    return (id, command) -> {
      var streamName = toStreamName.apply(id);

      EntityStore.getAndUpdate(
        eventClass,
        eventStore,
        evolve,
        getEmpty,
        handle,
        streamName,
        command
      );
    };
  }
}


