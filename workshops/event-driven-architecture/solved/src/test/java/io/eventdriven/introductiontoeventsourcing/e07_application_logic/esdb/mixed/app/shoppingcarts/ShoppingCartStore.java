package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.entities.EntityNotFoundException;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventstore.EsdbEventStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final EsdbEventStore eventStore;

  public ShoppingCartStore(EsdbEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    var result = eventStore.<ShoppingCart, ShoppingCartEvent>aggregateStream(
      ShoppingCart::initial,
      (state, event) -> {
        state.evolve(event);
        return state;
      },
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
      (state, event) -> {
        state.evolve(event);
        return state;
      },
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
