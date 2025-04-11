package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.immutable.app.shoppingcarts;

import io.eventdriven.eventstores.StreamName;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.entities.EntityNotFoundException;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.functional.Tuple;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.http.ETag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final MongoDBEventStore eventStore;

  public ShoppingCartStore(MongoDBEventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<Tuple<ShoppingCart, ETag>> get(UUID id) {
    var result = eventStore.aggregateStream(
      ShoppingCart::initial,
      ShoppingCart::evolve,
      toStreamName(id)
    );

    return result.streamExists() ?
      Optional.of(new Tuple<>(result.state(), ETag.weak(result.currentStreamPosition())))
      : Optional.empty();
  }

  public ETag add(UUID id, ShoppingCartEvent event) {
    return ETag.weak(
      eventStore.appendToStream(toStreamName(id), List.of(event))
        .nextExpectedStreamPosition()
    );
  }

  public ETag getAndUpdate(
    UUID id,
    ETag expectedVersion,
    Function<ShoppingCart, ShoppingCartEvent> handle
  ) {
    return ETag.weak(
      eventStore
        .getAndUpdate(
          ShoppingCart::initial,
          ShoppingCart::evolve,
          toStreamName(id),
          expectedVersion.toLong(),
          (state) -> {
            if (state.status() == null)
              throw new EntityNotFoundException();

            return List.of(handle.apply(state));
          }
        )
        .nextExpectedStreamPosition()
    );
  }

  private StreamName toStreamName(UUID id) {
    return new StreamName("shopping_cart", id.toString());
  }
}
