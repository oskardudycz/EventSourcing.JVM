package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStoreTests;
import io.eventdriven.distributedprocesses.core.kurrentdb.KurrentDBEventStore;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.distributedprocesses.ecommerce.ECommerceMessages;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
public class ShoppingCartExternalEventKafkaDownTests {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(4);
    private static final String FORWARDER = "shopping-carts-external-forwarder";

    private static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:8.4.1"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    private final OffsetDateTime now = OffsetDateTime.now();
    private final ProcessorCheckpoints checkpoints = H2.bean(ProcessorCheckpoints.class);

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    private KafkaMessageBus messageBus;
    private KurrentDBEventStore eventStore;

    @BeforeAll
    static void startKafka() {
        kafka.start();
    }

    @AfterAll
    static void stopKafka() {
        kafka.stop();
    }

    @AfterEach
    void close() {
        if (isPaused()) unpause();
        eventStore.close();
        messageBus.close();
    }

    @Test
    public void forwarderPublishFailsWithinTheTimeoutAndKeepsItsCheckpointWhileKafkaIsDown() {
        var shoppingCarts = shoppingCartsOnKurrentDBAndKafka();
        var warmUpCart = checkOutCartOfNewCustomer(shoppingCarts);
        awaitCheckoutInTheChannel(warmUpCart);
        var checkpointBeforeOutage = awaitCheckpointToSettle();
        var publishAttempts = new AtomicInteger();
        messageBus.use(message -> publishAttempts.incrementAndGet());

        pause();
        var cart = checkOutCartOfNewCustomer(shoppingCarts);
        await().atMost(Duration.ofSeconds(30)).until(() -> publishAttempts.get() >= 2);

        assertThat(checkpoints.lastProcessedPosition(FORWARDER)).hasValue(checkpointBeforeOutage);
        assertThatThrownBy(() -> messageBus.publish(List.of(checkoutOf(cart)))).isInstanceOf(RuntimeException.class);

        unpause();
        awaitCheckoutInTheChannel(cart);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> checkpoints.lastProcessedPosition(FORWARDER).orElse(0) > checkpointBeforeOutage);
    }

    @Test
    public void publishFailsWithinThePublishTimeoutWhileKafkaIsDown() {
        var shoppingCarts = shoppingCartsOnKurrentDBAndKafka();
        awaitCheckoutInTheChannel(checkOutCartOfNewCustomer(shoppingCarts));
        pause();
        var startedAt = System.nanoTime();

        assertThatThrownBy(() -> messageBus.publish(List.of(checkoutOf(ShoppingCartId.of(UUID.randomUUID(), 1)))))
                .isInstanceOf(RuntimeException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PUBLISH_TIMEOUT.plusSeconds(1));
    }

    private ShoppingCartFacade shoppingCartsOnKurrentDBAndKafka() {
        eventStore = KurrentDBEventStore.connectingTo(
                TestDatabases.kurrentDBConnectionString(),
                messageContext,
                observations,
                checkpoints,
                RetryBackOff.exponential(Duration.ofMillis(100)));
        messageBus = KafkaMessageBus.connectingTo(
                kafka.getBootstrapServers(),
                messageContext,
                observations,
                TestMessages.serializer(ECommerceMessages.mapping()),
                PUBLISH_TIMEOUT,
                TestKafka.failureHandling(
                        kafka.getBootstrapServers(), observations, new SimpleMeterRegistry(), PUBLISH_TIMEOUT));
        startFromTheEndOfTheLog();

        return ShoppingCartsConfig.configure(
                        eventStore,
                        messageBus,
                        item -> new PricedProductItem(item, 12.5),
                        H2.bean(CustomerShoppingCartRepository.class),
                        H2.bean(ShoppingCartSummaryRepository.class),
                        H2.bean(CartRepository.class),
                        () -> now)
                .facade();
    }

    private void startFromTheEndOfTheLog() {
        var marker = (EventStore.AppendResult.Success)
                eventStore.append("marker-" + UUID.randomUUID(), new EventStoreTests.ItemAdded("marker", "marker"));
        var position = marker.logPosition().getCommitUnsigned();

        for (var name : List.of(FORWARDER, "customer-shopping-carts", "shopping-cart-summaries", "carts"))
            checkpoints.handle(name, position, () -> {});
    }

    private long awaitCheckpointToSettle() {
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(30))
                .until(() -> checkpoints.lastProcessedPosition(FORWARDER).isPresent());

        return checkpoints.lastProcessedPosition(FORWARDER).orElseThrow();
    }

    private void awaitCheckoutInTheChannel(ShoppingCartId cartId) {
        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(
                                KafkaTopics.recordsOf(kafka.getBootstrapServers(), ShoppingCartsConfig.external))
                        .extracting(record -> record.key())
                        .contains(Urns.routingKeyOf(cartId.value())));
    }

    private ShoppingCartId checkOutCartOfNewCustomer(ShoppingCartFacade shoppingCarts) {
        var customerId = UUID.randomUUID();
        var cartId = ShoppingCartId.of(customerId, 1);
        shoppingCarts.addProductItem(
                new AddProductItemToShoppingCart(customerId, new ProductItem(UUID.randomUUID(), 2)));

        shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, 0L));

        return cartId;
    }

    private ShoppingCartExternalEvent.ShoppingCartCheckedOut checkoutOf(ShoppingCartId cartId) {
        return new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                cartId, UUID.randomUUID(), new PricedProductItem[0], 0, now);
    }

    private void pause() {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    private void unpause() {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }

    private boolean isPaused() {
        return Boolean.TRUE.equals(kafka.getDockerClient()
                .inspectContainerCmd(kafka.getContainerId())
                .exec()
                .getState()
                .getPaused());
    }
}
