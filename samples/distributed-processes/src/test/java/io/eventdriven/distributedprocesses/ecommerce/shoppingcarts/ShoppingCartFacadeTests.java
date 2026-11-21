package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
import java.time.OffsetDateTime;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShoppingCartFacadeTests {
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId firstCartId = ShoppingCartId.of(customerId, 1);
    private final ShoppingCartId secondCartId = ShoppingCartId.of(customerId, 2);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 3);
    private final Map<UUID, Double> unitPrices = new HashMap<>(Map.of(shoes.productId(), 12.5, shirt.productId(), 5.0));
    private final OffsetDateTime now = OffsetDateTime.now();

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced());
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
    public void theCustomersFirstProductStartsTheirFirstCart() {
        var eTag = shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(firstCartId, customerId, new PricedProductItem(shoes, 12.5), now));
        assertThat(eTag.toLong()).isEqualTo(0);
    }

    @Test
    public void anotherProductGoesToTheCartThatIsStillOpen() {
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));
        published.reset();

        var eTag = shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shirt));

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(firstCartId, customerId, new PricedProductItem(shirt, 5), now));
        assertThat(eTag.toLong()).isEqualTo(1);
    }

    @Test
    public void aProductAddedAfterCheckoutStartsTheNextCart() {
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));
        shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 0L));
        published.reset();

        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shirt));

        published.shouldReceiveMessages(
                new ProductItemAddedToShoppingCart(secondCartId, customerId, new PricedProductItem(shirt, 5), now));
    }

    @Test
    public void twoRequestsStartingTheSameCartEndUpInOneCart() {
        beforePricing = () -> shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shirt));

        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));

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
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));
        unitPrices.put(shoes.productId(), 20.0);
        published.reset();

        var oneShoe = new ProductItem(shoes.productId(), 1);
        var eTag = shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(firstCartId, oneShoe, 0L));

        published.shouldReceiveMessages(
                new ProductItemRemovedFromShoppingCart(firstCartId, new PricedProductItem(oneShoe, 12.5), now));
        assertThat(eTag.toLong()).isEqualTo(1);
    }

    @Test
    public void checkOutWithUnchangedPricesChecksTheCartOut() {
        addShoesAndShirt();

        var eTag = shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L));

        published.shouldReceiveMessages(new ShoppingCartCheckedOut(firstCartId, now));
        assertThat(eTag.toLong()).isEqualTo(2);
    }

    @Test
    public void checkOutAfterAPriceChangeRecordsTheChangeAndTheCustomerChecksOutAgainWithTheNewVersion() {
        addShoesAndShirt();
        unitPrices.put(shoes.productId(), 15.0);

        var afterPriceChange = shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L));

        published.shouldReceiveMessages(new ProductItemPriceChanged(firstCartId, shoes.productId(), 12.5, 15, now));
        assertThat(afterPriceChange.toLong()).isEqualTo(2);
        published.reset();

        assertThatThrownBy(() -> shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, 1L)))
                .isInstanceOf(ConcurrentModificationException.class);
        published.shouldNotReceiveAnyEvent();

        shoppingCarts.checkOut(new CheckOutShoppingCart(firstCartId, afterPriceChange.toLong()));

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
        assertThatThrownBy(() -> messageBus.publish(List.of(new AddProductItemToShoppingCart(customerId, shoes))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AddProductItemToShoppingCart.class.getName());

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void mapsIdToItsOwnStream() {
        assertThat(ShoppingCartFacade.mapToStreamId(firstCartId)).isEqualTo("ShoppingCart-%s-1".formatted(customerId));
    }

    private void addShoesAndShirt() {
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shoes));
        shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, shirt));
        published.reset();
    }

    private List<Object> storedEvents(ShoppingCartId shoppingCartId) {
        return switch (eventStore.read(ShoppingCartFacade.mapToStreamId(shoppingCartId))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }
}
