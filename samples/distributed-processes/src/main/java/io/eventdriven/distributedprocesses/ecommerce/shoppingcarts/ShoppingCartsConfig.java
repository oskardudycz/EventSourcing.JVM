package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Carts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

public final class ShoppingCartsConfig {
    public static final String external = "shopping_carts-external";

    private ShoppingCartsConfig() {}

    public static ShoppingCartsModule configure(
            EventStore eventStore,
            MessageBus messageBus,
            ProductPriceCalculator prices,
            CustomerShoppingCartRepository customerShoppingCartRepository,
            ShoppingCartSummaryRepository shoppingCartSummaryRepository,
            CartRepository cartRepository,
            Supplier<OffsetDateTime> now) {
        var store = new AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId>(
                eventStore, ShoppingCartFacade::mapToStreamId, ShoppingCart::empty);

        var customerShoppingCarts = new CustomerShoppingCarts(customerShoppingCartRepository);

        eventStore.subscribe(
                "customer-shopping-carts", customerShoppingCarts.canHandle(), customerShoppingCarts::handle);

        var facade = new ShoppingCartFacade(store, customerShoppingCarts, prices, now);

        var summaries = new ShoppingCartSummaries(shoppingCartSummaryRepository);

        eventStore.subscribe("shopping-cart-summaries", summaries.canHandle(), summaries::handle);

        messageBus
                .routes(new MessageRoutes()
                        .route(
                                ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
                                event -> new RoutingTarget(
                                        external,
                                        Urns.routingKeyOf(event.cartId().value()))))
                .channel(external);

        var forwarder = new ShoppingCartExternalEventForwarder(summaries, messageBus);

        eventStore.subscribe(
                "shopping-carts-external-forwarder", List.of(ShoppingCartCheckedOut.class), forwarder::handle);

        var carts = new Carts(cartRepository);

        eventStore.subscribe("carts", carts.canHandle(), carts::handle);

        return new ShoppingCartsModule(facade, customerShoppingCarts, carts);
    }
}
