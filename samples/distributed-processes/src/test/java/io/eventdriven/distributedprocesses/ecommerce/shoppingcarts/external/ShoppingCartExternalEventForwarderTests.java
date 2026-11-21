package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.Metadata;
import io.eventdriven.testing.TestTracing;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartExternalEventForwarderTests {
    private static final double unitPrice = 12.5;

    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId shoppingCartId = ShoppingCartId.of(customerId, 1);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
    private final PricedProductItem pricedShoes = new PricedProductItem(shoes, unitPrice);
    private final PricedProductItem pricedShirt = new PricedProductItem(shirt, unitPrice);
    private final Map<UUID, Double> unitPrices =
            new HashMap<>(Map.of(shoes.productId(), unitPrice, shirt.productId(), unitPrice));
    private final ProductPriceCalculator prices = item -> new PricedProductItem(item, unitPrices.get(item.productId()));
    private final OffsetDateTime now = OffsetDateTime.now();

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced());
    private final MessageCatcher externalEvents = new MessageCatcher();
    private final ShoppingCartFacade shoppingCarts;

    public ShoppingCartExternalEventForwarderTests() {
        messageBus.use(message -> {
            if (message instanceof ShoppingCartExternalEvent) externalEvents.catchMessage(message);
        });
        shoppingCarts = ShoppingCartsConfig.configure(
                        eventStore,
                        messageBus,
                        prices,
                        H2.bean(CustomerShoppingCartRepository.class),
                        H2.bean(ShoppingCartSummaryRepository.class),
                        H2.bean(CartRepository.class),
                        () -> now)
                .facade();
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

        shoppingCarts.checkOut(new CheckOutShoppingCart(shoppingCartId, afterPriceChange.toLong()));

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
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shirt));
    }

    @Test
    public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
        var summaries = new ShoppingCartSummaries(H2.bean(ShoppingCartSummaryRepository.class));
        var bus = new InMemoryMessageBus(TestTracing.untraced())
                .routes(new MessageRoutes()
                        .routeTarget(
                                MessageRoutes.inPackage("io.eventdriven.distributedprocesses.ecommerce"), "published"))
                .channel("published");
        var published = new MessageCatcher();
        bus.use(published::catchMessage);
        var forwarder = new ShoppingCartExternalEventForwarder(summaries, bus);
        var streamId = ShoppingCartFacade.mapToStreamId(shoppingCartId);

        summaries.handle(new EventEnvelope<>(
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, customerId, pricedShoes, now),
                Metadata.at(streamId, 0)));
        var checkedOut = new EventEnvelope<>(
                new ShoppingCartEvent.ShoppingCartCheckedOut(shoppingCartId, now), Metadata.at(streamId, 1));

        assertThatThrownBy(() -> forwarder.onCheckedOut(checkedOut)).isInstanceOf(IllegalStateException.class);
        published.shouldNotReceiveAnyEvent();

        summaries.handle(checkedOut);
        forwarder.onCheckedOut(checkedOut);

        published.shouldReceiveMessages(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                shoppingCartId, customerId, new PricedProductItem[] {pricedShoes}, 25, now));
    }
}
