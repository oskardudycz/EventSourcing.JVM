package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.immutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.functional.Tuple;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.core.http.ETag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final EventStore eventStore;

  public ShoppingCartStore(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<Tuple<ShoppingCart, ETag>> get(UUID id) {
    return eventStore.aggregateStream(
        ShoppingCartEvent.class,
        ShoppingCart::evolve,
        ShoppingCart::initial,
        toStreamName(id)
      )
      .map(r -> new Tuple<>(r.first(), ETag.weak(r.second())));
  }

  public ETag add(UUID id, ShoppingCartEvent event) {
    return ETag.weak(
      eventStore.add(toStreamName(id), new Object[]{event})
    );
  }

  public ETag getAndUpdate(UUID id, ETag eTag, Function<ShoppingCart, ShoppingCartEvent> handle) {
    return ETag.weak(
      eventStore.getAndUpdate(
        ShoppingCartEvent.class,
        ShoppingCart::evolve,
        ShoppingCart::initial,
        toStreamName(id),
        eTag.toLong(),
        (state) -> List.of(handle.apply(state))
      )
    );
  }

  private String toStreamName(UUID id) {
    return "shopping_cart-" + id.toString();
  }
}
