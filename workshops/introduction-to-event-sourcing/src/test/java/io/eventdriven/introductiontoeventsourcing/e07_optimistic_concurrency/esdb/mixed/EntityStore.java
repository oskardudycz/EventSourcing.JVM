package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mixed;

import com.eventstore.dbclient.EventStoreDBClient;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityStore<Entity extends BusinessLogic.Aggregate<Event>, Event, Command> {
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
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public <C extends Command> void add(UUID id, Event event) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public <C extends Command> void getAndUpdate(
    UUID id,
    C command,
    long expectedRevision,
    BiFunction<C, Entity, Event> commandHandler
  ) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }
}
