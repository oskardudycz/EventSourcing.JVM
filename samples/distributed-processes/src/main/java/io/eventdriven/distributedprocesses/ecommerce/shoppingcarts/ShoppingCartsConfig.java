package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Carts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public final class ShoppingCartsConfig {
  public static final String external = "shopping_carts-external";
  private ShoppingCartsConfig() {
  }

  public static ShoppingCartsModule configure(
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

    var customerShoppingCarts = new CustomerShoppingCarts(new Database());

    for (var eventType : customerShoppingCarts.canHandle()) {
      eventStore.subscribe(eventType, customerShoppingCarts::handle);
    }

    var facade = new ShoppingCartFacade(store, customerShoppingCarts, prices, now);

    var summaries = new ShoppingCartSummaries(new Database());

    for (var eventType : summaries.canHandle()) {
      eventStore.subscribe(eventType, summaries::handle);
    }

    messageBus.routes(new MessageRoutes()
      .route(ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
        event -> new RoutingTarget(external, Urns.routingKeyOf(event.cartId().value()))));

    var forwarder = new ShoppingCartExternalEventForwarder(summaries, messageBus);

    eventStore
      .subscribe(ShoppingCartCheckedOut.class, forwarder::onCheckedOut);

    var carts = new Carts(new Database());

    for (var eventType : carts.canHandle()) {
      eventStore.subscribe(eventType, carts::handle);
    }

    return new ShoppingCartsModule(facade, customerShoppingCarts, carts);
  }
}
