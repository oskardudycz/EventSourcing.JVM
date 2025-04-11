package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.immutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.postgresql.PostgreSQLEventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.entities.EntityNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final PostgreSQLEventStore eventStore;

  public ShoppingCartStore(PostgreSQLEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    var result =  eventStore.aggregateStream(
      ShoppingCart::initial,
      ShoppingCart::evolve,
      toStreamName(id)
    );

    return result.streamExists() ?
      Optional.of(result.state())
      : Optional.empty();
  }

  public void add(UUID id, ShoppingCartEvent event) {
    eventStore.appendToStream(toStreamName(id), List.of(event));
  }

  public void getAndUpdate(UUID id, Function<ShoppingCart, ShoppingCartEvent> handle) {
    eventStore.getAndUpdate(
      ShoppingCart::initial,
      ShoppingCart::evolve,
      toStreamName(id),
      (state) -> {
        if (state.status() == null)
          throw new EntityNotFoundException();

        return List.of(handle.apply(state));
      }
    );
  }

  private StreamName toStreamName(UUID id) {
    return new StreamName("shopping_cart", id.toString());
  }
}
