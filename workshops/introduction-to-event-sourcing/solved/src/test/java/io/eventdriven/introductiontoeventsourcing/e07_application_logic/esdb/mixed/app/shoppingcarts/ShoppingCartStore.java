package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.eventStoreDB.EventStore;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCartEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class ShoppingCartStore {
  private final EventStore eventStore;

  public ShoppingCartStore(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public Optional<io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart> get(UUID id) {
    return eventStore.aggregateStream(
      io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCartEvent.class,
      io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart::evolve,
      io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart::initial,
      toStreamName(id)
    );
  }

  public void add(UUID id, io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCartEvent event) {
    eventStore.add(toStreamName(id), new Object[]{event});
  }

  public void getAndUpdate(UUID id, Function<io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart, io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCartEvent> handle) {
    eventStore.getAndUpdate(
      ShoppingCartEvent.class,
      io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.mixed.app.shoppingcarts.ShoppingCart::evolve,
      ShoppingCart::initial,
      toStreamName(id),
      (state) -> List.of(handle.apply(state))
    );
  }

  private String toStreamName(UUID id) {
    return "shopping_cart-" + id.toString();
  }
}
