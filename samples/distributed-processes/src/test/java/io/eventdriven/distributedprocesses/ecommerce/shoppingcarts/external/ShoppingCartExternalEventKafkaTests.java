package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.ecommerce.ECommerceMessages;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
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
        var eventStore = new InMemoryEventStore(messageContext);
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                TestMessages.serializer(ECommerceMessages.mapping()),
                Duration.ofSeconds(10),
                TestKafka.failureHandling(bootstrapServers, observations));
        var shoppingCarts = ShoppingCartsConfig.configure(
                        eventStore,
                        messageBus,
                        item -> new PricedProductItem(item, 12.5),
                        H2.bean(CustomerShoppingCartRepository.class),
                        H2.bean(ShoppingCartSummaryRepository.class),
                        H2.bean(CartRepository.class),
                        () -> now)
                .facade();
        var firstCart = checkOutCartOfNewCustomer(shoppingCarts);
        var secondCart = checkOutCartOfNewCustomer(shoppingCarts);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(KafkaTopics.recordsOf(bootstrapServers, ShoppingCartsConfig.external))
                        .extracting(record -> record.key())
                        .contains(Urns.routingKeyOf(firstCart.value()), Urns.routingKeyOf(secondCart.value())));
    }

    private ShoppingCartId checkOutCartOfNewCustomer(ShoppingCartFacade shoppingCarts) {
        var customerId = UUID.randomUUID();
        var cartId = ShoppingCartId.of(customerId, 1);
        shoppingCarts.addProductItem(
                new AddProductItemToShoppingCart(customerId, new ProductItem(UUID.randomUUID(), 2)));
        shoppingCarts.addProductItem(
                new AddProductItemToShoppingCart(customerId, new ProductItem(UUID.randomUUID(), 3)));

        shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, 1L));

        return cartId;
    }
}
