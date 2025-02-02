package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.entities.EntityNotFoundException;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.functional.Tuple;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.http.ETag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final MongoDBEventStore eventStore;

  public ShoppingCartStore(MongoDBEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<Tuple<ShoppingCart, ETag>> get(UUID id) {
    var result = eventStore.<ShoppingCart, ShoppingCartEvent>aggregateStream(
      ShoppingCart::initial,
      (cart, event) -> {
        cart.evolve(event);
        return cart;
      },
      toStreamName(id)
    );

    return result.streamExists() ?
      Optional.of(new Tuple<>(result.state(), ETag.weak(result.currentStreamPosition())))
      : Optional.empty();
  }

  public ETag add(UUID id, ShoppingCart shoppingCart) {
    return ETag.weak(
      eventStore.appendToStream(toStreamName(id), List.copyOf(shoppingCart.dequeueUncommittedEvents()))
        .nextExpectedStreamPosition()
    );
  }

  public ETag getAndUpdate(
    UUID id,
    ETag expectedVersion,
    Consumer<ShoppingCart> handle
  ) {
    return ETag.weak(
      eventStore
        .getAndUpdate(
          ShoppingCart::initial,
          (state, event) -> {
            state.evolve(event);
            return state;
          },
          toStreamName(id),
          expectedVersion.toLong(),
          (state) -> {
            if (state.status() == null)
              throw new EntityNotFoundException();

            handle.accept(state);

            return state.dequeueUncommittedEvents();
          }
        )
        .nextExpectedStreamPosition()
    );
  }

  private StreamName toStreamName(UUID id) {
    return new StreamName("shopping_cart", id.toString());
  }
}
