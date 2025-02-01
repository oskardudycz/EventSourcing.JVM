package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.entities.EntityNotFoundException;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.core.eventstore.EsdbEventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.shoppingcarts.ShoppingCartEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final EsdbEventStore eventStore;

  public ShoppingCartStore(EsdbEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<ShoppingCart> get(UUID id) {
    var result = eventStore.<ShoppingCart, ShoppingCartEvent>aggregateStream(
      ShoppingCart::initial,
      (cart, event) -> {
        cart.evolve(event);
        return cart;
      },
      toStreamName(id)
    );

    return result.streamExists() ?
      Optional.of(result.state())
      : Optional.empty();
  }

  public void add(UUID id, ShoppingCart shoppingCart) {
    eventStore.appendToStream(toStreamName(id), List.copyOf(shoppingCart.dequeueUncommittedEvents()));
  }

  public void getAndUpdate(UUID id, Consumer<ShoppingCart> handle) {
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

        handle.accept(state);

        return state.dequeueUncommittedEvents();
      }
    );
  }

  private StreamName toStreamName(UUID id) {
    return new StreamName("shopping_cart", id.toString());
  }
}
