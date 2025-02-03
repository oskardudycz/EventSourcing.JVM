package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final MongoDBEventStore eventStore;

  public ShoppingCartStore(MongoDBEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    throw new RuntimeException("Not implemented yet!");
  }

  public void add(UUID id, ShoppingCart shoppingCart) {
    throw new RuntimeException("Not implemented yet!");
  }

  public void getAndUpdate(UUID id, Consumer<ShoppingCart> handle) {
    throw new RuntimeException("Not implemented yet!");
  }

  private StreamName toStreamName(UUID id) {
    return new StreamName("shopping_cart", id.toString());
  }
}
