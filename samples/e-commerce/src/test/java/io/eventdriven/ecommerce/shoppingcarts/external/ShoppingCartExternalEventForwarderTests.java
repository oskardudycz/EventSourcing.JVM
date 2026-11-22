package io.eventdriven.ecommerce.shoppingcarts.external;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCartRepository;
import io.eventdriven.ecommerce.shoppingcarts.getcart.CartRepository;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartExternalEventForwarderTests {
    private static final double unitPrice = 12.5;

    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id shoppingCartId = ShoppingCart.Id.of(customerId, 1);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
    private final PricedProductItem pricedShoes = new PricedProductItem(shoes, unitPrice);
    private final PricedProductItem pricedShirt = new PricedProductItem(shirt, unitPrice);
    private final Map<UUID, Double> unitPrices =
            new HashMap<>(Map.of(shoes.productId(), unitPrice, shirt.productId(), unitPrice));
    private final ProductPriceCalculator prices = item -> new PricedProductItem(item, unitPrices.get(item.productId()));
    private final OffsetDateTime now = OffsetDateTime.now();

    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(TestTracing.untraced(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(ShoppingCartsConfig.shoppingCartsRoutes()),
            List.of(ShoppingCartsConfig.shoppingCartsExternalChannel()),
            List.of());
    private final MessageCatcher externalEvents = new MessageCatcher();
    private final ShoppingCartFacade shoppingCarts;

    public ShoppingCartExternalEventForwarderTests() {
        messageBus.use(envelope -> {
            if (envelope.message() instanceof ShoppingCartExternalEvent)
                externalEvents.catchMessage(envelope.message());
        });
        var shoppingCartsConfig = new ShoppingCartsConfig();
        var customerShoppingCarts =
                shoppingCartsConfig.customerShoppingCarts(eventStore, H2.bean(CustomerShoppingCartRepository.class));
        shoppingCarts = shoppingCartsConfig.shoppingCartFacade(
                eventStore, TestTracing.untraced(), customerShoppingCarts, prices, () -> now);
        var shoppingCartSummaries =
                shoppingCartsConfig.shoppingCartSummaries(eventStore, H2.bean(ShoppingCartSummaryRepository.class));
        shoppingCartsConfig.shoppingCartExternalEventForwarder(eventStore, shoppingCartSummaries, messageBus);
        shoppingCartsConfig.carts(eventStore, H2.bean(CartRepository.class));
    }

    @Test
    public void checkingOutCartWithTwoProductItemsForwardsShoppingCartCheckedOut() {
        addShoesAndShirt();

        shoppingCarts.checkOut(new CheckOutShoppingCart(shoppingCartId, 1L));

        externalEvents.shouldReceiveMessages(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                shoppingCartId, customerId, new PricedProductItem[] {pricedShoes, pricedShirt}, 62.5, now));
    }

    @Test
    public void checkingOutAfterAPriceChangeForwardsTheNewPrices() {
        addShoesAndShirt();
        unitPrices.put(shoes.productId(), 15.0);
        var afterPriceChange = shoppingCarts.checkOut(new CheckOutShoppingCart(shoppingCartId, 1L));

        externalEvents.shouldNotReceiveAnyEvent();

        shoppingCarts.checkOut(new CheckOutShoppingCart(shoppingCartId, afterPriceChange));

        externalEvents.shouldReceiveMessages(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                shoppingCartId,
                customerId,
                new PricedProductItem[] {new PricedProductItem(shoes, 15), pricedShirt},
                67.5,
                now));
    }

    @Test
    public void fillingACartForwardsNothing() {
        addShoesAndShirt();

        externalEvents.shouldNotReceiveAnyEvent();
    }

    @Test
    public void removingProductItemForwardsNothing() {
        addShoesAndShirt();

        shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(shoppingCartId, shoes, 1L));

        externalEvents.shouldNotReceiveAnyEvent();
    }

    private void addShoesAndShirt() {
        shoppingCarts.addProductItem(customerId, shoes);
        shoppingCarts.addProductItem(customerId, shirt);
    }

    @Test
    public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
        var summaries = new ShoppingCartSummaries(H2.bean(ShoppingCartSummaryRepository.class));
        var bus = new InMemoryMessageBus(
                TestTracing.untraced(),
                List.of(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), "published")),
                List.of(new MessageChannel("published")),
                List.of());
        var published = new MessageCatcher();
        bus.use(envelope -> published.catchMessage(envelope.message()));
        var forwarder = new ShoppingCartExternalEventForwarder(summaries, bus);
        var streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);

        summaries.handle(new MessageEnvelope<>(
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, customerId, pricedShoes, now),
                Metadata.at(streamId, 0)));
        MessageEnvelope<Object> checkedOut = new MessageEnvelope<>(
                new ShoppingCartEvent.ShoppingCartCheckedOut(shoppingCartId, now), Metadata.at(streamId, 1));

        assertThatThrownBy(() -> forwarder.handle(checkedOut)).isInstanceOf(IllegalStateException.class);
        published.shouldNotReceiveAnyEvent();

        summaries.handle(checkedOut);
        forwarder.handle(checkedOut);

        published.shouldReceiveMessages(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                shoppingCartId, customerId, new PricedProductItem[] {pricedShoes}, 25, now));
    }
}
