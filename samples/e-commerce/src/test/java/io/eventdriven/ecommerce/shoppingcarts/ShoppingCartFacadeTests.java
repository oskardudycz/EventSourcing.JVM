package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCartRepository;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.ecommerce.shoppingcarts.getcart.CartRepository;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartFacadeTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCart.Id firstCartId = ShoppingCart.Id.of(customerId, 1);
    private final ShoppingCart.Id secondCartId = ShoppingCart.Id.of(customerId, 2);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
    private final Map<UUID, Double> unitPrices = new HashMap<>(Map.of(shoes.productId(), 12.5, shirt.productId(), 5.0));
    private final OffsetDateTime now = OffsetDateTime.now();

    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(TestTracing.untraced(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(ShoppingCartsConfig.shoppingCartsRoutes()),
            List.of(ShoppingCartsConfig.shoppingCartsExternalChannel()),
            List.of());
    private final MessageCatcher published = new MessageCatcher();
    private Runnable beforePricing = () -> {};
    private final ProductPriceCalculator prices = item -> {
        var run = beforePricing;
        beforePricing = () -> {};
        run.run();
        return new PricedProductItem(item, unitPrices.get(item.productId()));
    };
    private final ShoppingCartFacade shoppingCarts;

    public ShoppingCartFacadeTests() {
        eventStore.use(published::catchMessage);
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
    public void theCustomersFirstProductStartsTheirFirstCart() {
        var version = shoppingCarts.addProductItem(customerId, shoes);

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(firstCartId, customerId, new PricedProductItem(shoes, 12.5), now));
        assertThat(version).isEqualTo(0);
    }

    @Test
    public void addingAProductToACartWithOneProductAppendsAtVersionOne() {
        shoppingCarts.addProductItem(customerId, shoes);

        var version = shoppingCarts.addProductItem(customerId, shirt);

        assertThat(version).isEqualTo(1);
        assertThat(storedEvents(firstCartId)).hasSize(2);
    }

    @Test
    public void anotherProductGoesToTheCartThatIsStillOpen() {
        shoppingCarts.addProductItem(customerId, shoes);
        published.reset();

        var version = shoppingCarts.addProductItem(customerId, shirt);

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(firstCartId, customerId, new PricedProductItem(shirt, 5), now));
        assertThat(version).isEqualTo(1);
    }

    @Test
    public void aProductAddedAfterCheckoutStartsTheNextCart() {
        shoppingCarts.addProductItem(customerId, shoes);
        shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 0L));
        published.reset();

        shoppingCarts.addProductItem(customerId, shirt);

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(secondCartId, customerId, new PricedProductItem(shirt, 5), now));
    }

    @Test
    public void twoRequestsStartingTheSameCartEndUpInOneCart() {
        beforePricing = () -> shoppingCarts.addProductItem(customerId, shirt);

        shoppingCarts.addProductItem(customerId, shoes);

        assertThat(storedEvents(firstCartId))
                .usingRecursiveComparison()
                .isEqualTo(List.of(
                        new ProductItemAddedToShoppingCart(
                                firstCartId, customerId, new PricedProductItem(shirt, 5), now),
                        new ProductItemAddedToShoppingCart(
                                firstCartId, customerId, new PricedProductItem(shoes, 12.5), now)));
        assertThat(storedEvents(secondCartId)).isEmpty();
    }

    @Test
    public void removeProductItemRecordsItAtTheCartsPrice() {
        shoppingCarts.addProductItem(customerId, shoes);
        unitPrices.put(shoes.productId(), 20.0);
        published.reset();

        var oneShoe = new ProductItem(shoes.productId(), 1);
        var version = shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(firstCartId, oneShoe, 0L));

        published.shouldReceiveMessages(
                new ProductItemRemovedFromShoppingCart(firstCartId, new PricedProductItem(oneShoe, 12.5), now));
        assertThat(version).isEqualTo(1);
    }

    @Test
    public void checkOutWithUnchangedPricesChecksTheCartOut() {
        addShoesAndShirt();

        var version = shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L));

        published.shouldReceiveMessages(new ShoppingCartCheckedOut(firstCartId, now));
        assertThat(version).isEqualTo(2);
    }

    @Test
    public void checkOutAfterAPriceChangeRecordsTheChangeAndTheCustomerChecksOutAgainWithTheNewVersion() {
        addShoesAndShirt();
        unitPrices.put(shoes.productId(), 15.0);

        var afterPriceChange = shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L));

        published.shouldReceiveMessages(new ProductItemPriceChanged(firstCartId, shoes.productId(), 12.5, 15, now));
        assertThat(afterPriceChange).isEqualTo(2);
        published.reset();

        assertThatThrownBy(() -> shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L)))
                .isInstanceOf(ConcurrentModificationException.class);
        published.shouldNotReceiveAnyEvent();

        shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, afterPriceChange));

        published.shouldReceiveMessages(new ShoppingCartCheckedOut(firstCartId, now));
    }

    @Test
    public void commandWithStaleExpectedVersionFailsInsteadOfAppending() {
        addShoesAndShirt();

        assertThatThrownBy(() -> shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 0L)))
                .isInstanceOf(ConcurrentModificationException.class);

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents(firstCartId)).hasSize(2);
    }

    @Test
    public void customerCommandsAreNotTakenFromTheMessageBus() {
        assertThatThrownBy(() ->
                        messageBus.publish(List.of(new AddProductItemToShoppingCart(firstCartId, customerId, shoes))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AddProductItemToShoppingCart.class.getName());

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void mapsIdToItsOwnStream() {
        assertThat(ShoppingCartFacade.mapToStreamId(firstCartId)).isEqualTo("ShoppingCart-%s-1".formatted(customerId));
    }

    private void addShoesAndShirt() {
        shoppingCarts.addProductItem(customerId, shoes);
        shoppingCarts.addProductItem(customerId, shirt);
        published.reset();
    }

    private List<Object> storedEvents(ShoppingCart.Id shoppingCartId) {
        return switch (eventStore.read(ShoppingCartFacade.mapToStreamId(shoppingCartId))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }
}
