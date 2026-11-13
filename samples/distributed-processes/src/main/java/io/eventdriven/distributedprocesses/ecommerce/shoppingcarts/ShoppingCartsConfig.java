package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public final class ShoppingCartsConfig {
  private ShoppingCartsConfig() {
  }

  public static ShoppingCartFacade configure(
    EventStore eventStore,
    MessageBus messageBus,
    ProductPriceCalculator prices,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId>(
      eventStore,
      ShoppingCartFacade::mapToStreamId,
      ShoppingCart::empty
    );

    var clientShoppingCarts = new ClientShoppingCarts(new Database());

    for (var eventType : clientShoppingCarts.canHandle()) {
      eventStore.subscribe(eventType, clientShoppingCarts::handle);
    }

    var facade = new ShoppingCartFacade(store, clientShoppingCarts, prices, now);

    var summaries = new ShoppingCartSummaries(new Database());

    for (var eventType : summaries.canHandle()) {
      eventStore.subscribe(eventType, summaries::handle);
    }

    var forwarder = new ShoppingCartExternalEventForwarder(summaries, messageBus);

    eventStore
      .subscribe(ShoppingCartCheckedOut.class, forwarder::onCheckedOut);

    return facade;
  }
}
