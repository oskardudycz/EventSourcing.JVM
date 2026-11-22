package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.core.commands.CommandHandler;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCartRepository;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartSummaries;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.ecommerce.shoppingcarts.getcart.CartRepository;
import io.eventdriven.ecommerce.shoppingcarts.getcart.Carts;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShoppingCartsConfig {
    public static final String external = "shopping_carts-external";

    @Bean("shoppingCartsMapping")
    public static MessageMapping mapping() {
        return new MessageMapping()
                .trust(ShoppingCartsConfig.class.getPackageName(), ShoppingCartExternalEvent.class.getPackageName());
    }

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
            MessageContext messageContext,
            CustomerShoppingCarts customerShoppingCarts,
            ProductPriceCalculator prices,
            Supplier<OffsetDateTime> now) {
        var commandHandler = new CommandHandler<ShoppingCart.Id, ShoppingCart, ShoppingCartEvent>(
                eventStore,
                messageContext,
                ShoppingCartFacade::mapToStreamId,
                ShoppingCart::initial,
                ShoppingCart::evolve);

        return new ShoppingCartFacade(commandHandler, customerShoppingCarts, prices, now);
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
