package io.eventdriven.eventdrivenarchitecture.e07_application_logic.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.core.entities.EntityNotFoundException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final MongoDBEventStore eventStore;

  public ShoppingCartStore(MongoDBEventStore eventStore) {
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
    eventStore.appendToStream(toStreamName(id), new ArrayList<>(shoppingCart.dequeueUncommittedEvents()));
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
