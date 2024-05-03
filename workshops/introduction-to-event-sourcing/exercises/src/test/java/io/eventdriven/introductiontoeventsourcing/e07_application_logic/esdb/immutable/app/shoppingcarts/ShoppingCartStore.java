package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventStoreDB.EventStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final EventStore eventStore;

  public ShoppingCartStore(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    return eventStore.aggregateStream(
      ShoppingCartEvent.class,
      ShoppingCart::evolve,
      ShoppingCart::initial,
      toStreamName(id)
    );
  }

  public void add(UUID id, ShoppingCartEvent event) {
    eventStore.add(toStreamName(id), new Object[]{event});
  }

  public void getAndUpdate(UUID id, Function<ShoppingCart, ShoppingCartEvent> handle) {
    eventStore.getAndUpdate(
      ShoppingCartEvent.class,
      ShoppingCart::evolve,
      ShoppingCart::initial,
      toStreamName(id),
      (state) -> List.of(handle.apply(state))
    );
  }

  private String toStreamName(UUID id) {
    return "shopping_cart-" + id.toString();
  }
}
