package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.core.functional.Tuple;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.http.ETag;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ShoppingCartStore {
  private final EventStore eventStore;

  public ShoppingCartStore(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<Tuple<ShoppingCart, ETag>> get(UUID id) {
    return eventStore.aggregateStream(
        ShoppingCartEvent.class,
        ShoppingCart::initial,
        toStreamName(id)
      )
      .map(r -> new Tuple<>(r.first(), ETag.weak(r.second())));
  }

  public ETag add(UUID id, ShoppingCart shoppingCart) {
    return ETag.weak(
      eventStore.add(toStreamName(id), shoppingCart)
    );
  }

  public ETag getAndUpdate(UUID id, ETag eTag, Consumer<ShoppingCart> handle) {
    return ETag.weak(
      eventStore.getAndUpdate(
        ShoppingCartEvent.class,
        ShoppingCart::initial,
        toStreamName(id),
        eTag.toLong(),
        handle
      )
    );
  }

  private String toStreamName(UUID id) {
    return "shopping_cart-" + id.toString();
  }
}
