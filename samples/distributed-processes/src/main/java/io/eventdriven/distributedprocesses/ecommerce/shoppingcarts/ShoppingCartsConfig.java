package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShoppingCartsConfig {
    public static final String external = "shopping_carts-external";

    @Bean
    public static MessageRoutes shoppingCartsRoutes() {
        return new MessageRoutes()
                .route(
                        ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
                        event -> new RoutingTarget(
                                external, Urns.routingKeyOf(event.cartId().value())));
    }

    @Bean
    public static MessageChannel shoppingCartsExternalChannel() {
        return new MessageChannel(external);
    }

    @Bean
    public CustomerShoppingCarts customerShoppingCarts(
            EventStore eventStore, CustomerShoppingCartRepository customerShoppingCartRepository) {
        var customerShoppingCarts = new CustomerShoppingCarts(customerShoppingCartRepository);

        eventStore.subscribe(
                "customer-shopping-carts", customerShoppingCarts.canHandle(), customerShoppingCarts::handle);

        return customerShoppingCarts;
    }

    @Bean
    public ShoppingCartFacade shoppingCartFacade(
            EventStore eventStore,
            CustomerShoppingCarts customerShoppingCarts,
            ProductPriceCalculator prices,
            Supplier<OffsetDateTime> now) {
        var store = new AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId>(
                eventStore, ShoppingCartFacade::mapToStreamId, ShoppingCart::empty);

        return new ShoppingCartFacade(store, customerShoppingCarts, prices, now);
    }

    @Bean
    public ShoppingCartSummaries shoppingCartSummaries(
            EventStore eventStore, ShoppingCartSummaryRepository shoppingCartSummaryRepository) {
        var summaries = new ShoppingCartSummaries(shoppingCartSummaryRepository);

        eventStore.subscribe("shopping-cart-summaries", summaries.canHandle(), summaries::handle);

        return summaries;
    }

    @Bean
    public ShoppingCartExternalEventForwarder shoppingCartExternalEventForwarder(
            EventStore eventStore, ShoppingCartSummaries summaries, MessageBus messageBus) {
        var forwarder = new ShoppingCartExternalEventForwarder(summaries, messageBus);

        eventStore.subscribe(
                "shopping-carts-external-forwarder", List.of(ShoppingCartCheckedOut.class), forwarder::handle);

        return forwarder;
    }

    @Bean
    public Carts carts(EventStore eventStore, CartRepository cartRepository) {
        var carts = new Carts(cartRepository);

        eventStore.subscribe("carts", carts.canHandle(), carts::handle);

        return carts;
    }
}
