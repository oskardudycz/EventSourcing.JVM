package io.eventdriven.introductiontoeventsourcing.e07_optimistic_concurrency.esdb.mutable;

import com.eventstore.dbclient.EventStoreDBClient;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EntityStore<Entity extends BusinessLogic.Aggregate<Event>, Event> {
  private final Class<Entity> entityClass;
  private final Class<Event> eventClass;
  private final EventStoreDBClient eventStore;
  private final Function<UUID, String> toStreamId;

  public EntityStore(
    Class<Entity> entityClass,
    Class<Event> eventClass,
    EventStoreDBClient eventStore,
    Function<UUID, String> toStreamId
  ){
    this.entityClass = entityClass;
    this.eventClass = eventClass;
    this.eventStore = eventStore;
    this.toStreamId = toStreamId;
  }

  public Entity get(UUID id) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public void add(Entity entity) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }

  public <Command> void getAndUpdate(
    UUID id,
    Command command,
    long expectedRevision,
    BiConsumer<Command, Entity> commandHandler
  ) {
    // Fill logic here.
    throw new RuntimeException("Not implemented!");
  }
}
