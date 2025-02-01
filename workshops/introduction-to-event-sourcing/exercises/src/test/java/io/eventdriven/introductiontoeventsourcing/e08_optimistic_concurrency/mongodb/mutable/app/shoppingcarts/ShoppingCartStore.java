package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.core.eventStoreDB.EventStore;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final EventStore eventStore;

  public ShoppingCartStore(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    return eventStore.aggregateStream(
      ShoppingCartEvent.class,
      ShoppingCart::initial,
      toStreamName(id)
    );
  }

  public void add(UUID id, ShoppingCart shoppingCart) {
    eventStore.add(toStreamName(id), shoppingCart);
  }

  public void getAndUpdate(UUID id, Consumer<ShoppingCart> handle) {
    eventStore.getAndUpdate(
      ShoppingCartEvent.class,
      ShoppingCart::initial,
      toStreamName(id),
      handle
    );
  }

  private String toStreamName(UUID id) {
    return "shopping_cart-" + id.toString();
  }
}
