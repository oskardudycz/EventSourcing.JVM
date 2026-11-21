package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.RabbitMQMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.ECommerceMessages;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("in-memory")
public class ShoppingCartExternalEventRabbitMQTests {
    private static final RabbitMQContainer rabbitMQ = TestDatabases.newRabbitMQContainer();

    private final OffsetDateTime now = OffsetDateTime.now();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    private RabbitMQMessageBus messageBus;

    @BeforeAll
    static void startRabbitMQ() {
        rabbitMQ.start();
    }

    @AfterAll
    static void stopRabbitMQ() {
        rabbitMQ.stop();
    }

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
    }

    @Test
    public void publishToAChannelWithNoQueueSucceedsAndTheForwarderGoesOn() {
        var connection = TestDatabases.connectionTo(rabbitMQ);
        var eventStore = new InMemoryEventStore(messageContext);
        messageBus = TestRabbitMQ.busOn(
                connection, messageContext, applicationContext, TestMessages.serializer(ECommerceMessages.mapping()));
        var shoppingCarts = ShoppingCartsConfig.configure(
                        eventStore,
                        messageBus,
                        item -> new PricedProductItem(item, 12.5),
                        H2.bean(CustomerShoppingCartRepository.class),
                        H2.bean(ShoppingCartSummaryRepository.class),
                        H2.bean(CartRepository.class),
                        () -> now)
                .facade();

        checkOutCartOfNewCustomer(shoppingCarts);
        checkOutCartOfNewCustomer(shoppingCarts);

        var management = new RabbitMQManagement(connection);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(management.messageStatsOfExchange(ShoppingCartsConfig.external))
                        .containsEntry("publish_in", 2)
                        .doesNotContainKey("return_unroutable"));
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
