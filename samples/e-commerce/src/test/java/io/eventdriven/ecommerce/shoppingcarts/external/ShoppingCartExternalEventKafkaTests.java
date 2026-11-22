package io.eventdriven.ecommerce.shoppingcarts.external;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.kafka.KafkaMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCartRepository;
import io.eventdriven.ecommerce.shoppingcarts.getcart.CartRepository;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.messaging.kafka.KafkaTopics;
import io.eventdriven.testing.messaging.kafka.TestKafka;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class ShoppingCartExternalEventKafkaTests {
    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();
    private final OffsetDateTime now = OffsetDateTime.now();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    private KafkaMessageBus messageBus;

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
    }

    @Test
    public void publishToAChannelWithNoQueueSucceedsAndTheForwarderGoesOn() {
        var eventStore = new InMemoryEventStore(messageContext, ECommerceMessages.serializer());
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                ECommerceMessages.serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                List.of(ShoppingCartsConfig.shoppingCartsRoutes()),
                List.of(ShoppingCartsConfig.shoppingCartsExternalChannel()),
                List.of());
        var shoppingCartsConfig = new ShoppingCartsConfig();
        var customerShoppingCarts =
                shoppingCartsConfig.customerShoppingCarts(eventStore, H2.bean(CustomerShoppingCartRepository.class));
        var shoppingCarts = shoppingCartsConfig.shoppingCartFacade(
                eventStore,
                messageContext,
                customerShoppingCarts,
                item -> new PricedProductItem(item, 12.5),
                () -> now);
        var shoppingCartSummaries =
                shoppingCartsConfig.shoppingCartSummaries(eventStore, H2.bean(ShoppingCartSummaryRepository.class));
        shoppingCartsConfig.shoppingCartExternalEventForwarder(eventStore, shoppingCartSummaries, messageBus);
        shoppingCartsConfig.carts(eventStore, H2.bean(CartRepository.class));
        var firstCart = checkOutCartOfNewCustomer(shoppingCarts);
        var secondCart = checkOutCartOfNewCustomer(shoppingCarts);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(KafkaTopics.recordsOf(bootstrapServers, ShoppingCartsConfig.external))
                        .extracting(record -> record.key())
                        .contains(Urns.routingKeyOf(firstCart.value()), Urns.routingKeyOf(secondCart.value())));
    }

    private ShoppingCart.Id checkOutCartOfNewCustomer(ShoppingCartFacade shoppingCarts) {
        var customerId = UUID.randomUUID();
        var cartId = ShoppingCart.Id.of(customerId, 1);
        shoppingCarts.addProductItem(customerId, new ProductItem(UUID.randomUUID(), 2));
        shoppingCarts.addProductItem(customerId, new ProductItem(UUID.randomUUID(), 3));

        shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, 1L));

        return cartId;
    }
}
