package io.eventdriven.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.eventstore.kurrentdb.KurrentDBEventStore;
import io.eventdriven.core.eventstore.postgresql.PostgreSQLEventStore;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.core.messaging.kafka.KafkaMessageBus;
import io.eventdriven.core.messaging.rabbitmq.RabbitMQMessageBus;
import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.pending.PendingOrder;
import io.eventdriven.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.messaging.kafka.KafkaTopics;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQManagement;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQQueues;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ECommerceSettingsTests {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "messaging.bus | kafak | in-memory, kafka, rabbitmq",
                "eventstore.type | kurentdb | in-memory, kurrentdb, postgresql",
                "projections.database | mysql | h2, postgresql"
            })
    public void startFailsNamingTheSettingAndItsAllowedValues(String setting, String value, String allowedValues) {
        var application = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .profiles("in-memory")
                .properties("logging.level.org.springframework.boot.SpringApplication=OFF");

        assertThatThrownBy(() -> application.run("--" + setting + "=" + value))
                .rootCause()
                .hasMessageContaining(setting)
                .hasMessageContaining(allowedValues);
    }

    @DirtiesContext
    @SpringBootTest(properties = "eventstore.type=kurrentdb")
    @ActiveProfiles("in-memory")
    public static class KurrentDBEventStoreSetting {
        @DynamicPropertySource
        static void kurrentDB(DynamicPropertyRegistry registry) {
            registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        }

        @Autowired
        private EventStore eventStore;

        @Test
        public void kurrentDBSettingSelectsTheKurrentDBEventStore() {
            assertThat(eventStore).isInstanceOf(KurrentDBEventStore.class);
        }
    }

    @DirtiesContext
    @SpringBootTest(properties = "messaging.bus=kafka")
    @ActiveProfiles("in-memory")
    public static class KafkaMessageBusSetting {
        @DynamicPropertySource
        static void kafka(DynamicPropertyRegistry registry) {
            registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
        }

        @Autowired
        private MessageBus messageBus;

        @Test
        public void kafkaSettingSelectsTheKafkaMessageBusAndDeclaresTheModulesChannels() {
            assertThat(messageBus).isInstanceOf(KafkaMessageBus.class);
            assertThat(KafkaTopics.namesContaining(TestDatabases.kafkaBootstrapServers(), "payments-"))
                    .contains("payments-requests", "payments-responses", "payments-internal");
        }
    }

    @DirtiesContext
    @SpringBootTest(properties = "messaging.bus=rabbitmq")
    @ActiveProfiles("in-memory")
    public static class RabbitMQMessageBusSetting {
        @DynamicPropertySource
        static void rabbitMQ(DynamicPropertyRegistry registry) {
            TestDatabases.addRabbitMQ(registry, TestDatabases.rabbitMQ());
        }

        @Autowired
        private MessageBus messageBus;

        @Test
        public void rabbitMQSettingSelectsTheRabbitMQMessageBusAndDeclaresTheModulesChannels() {
            assertThat(messageBus).isInstanceOf(RabbitMQMessageBus.class);
            assertThat(new RabbitMQManagement(TestDatabases.rabbitMQ()).exchangeNames())
                    .contains("payments-requests", "payments-responses", "payments-internal");
        }
    }

    @DirtiesContext
    @SpringBootTest(properties = {"messaging.bus=rabbitmq", "messaging.retry.initial-interval=10ms"})
    @ActiveProfiles("in-memory")
    public static class RabbitMQDeadLettering {
        private static final String queue =
                "dead-lettering-" + UUID.randomUUID().toString().substring(0, 8);

        @TestConfiguration
        static class DeadLetteringQueue {
            @Bean
            static MessageQueue deadLetteringQueue() {
                return new MessageQueue(queue, ShoppingCartsConfig.external, "#");
            }
        }

        @DynamicPropertySource
        static void rabbitMQ(DynamicPropertyRegistry registry) {
            TestDatabases.addRabbitMQ(registry, TestDatabases.rabbitMQ());
        }

        @Autowired
        private MessageBus messageBus;

        @Autowired
        private MeterRegistry meters;

        @Test
        public void failingHandlerIsCalledOnceMoreThanTheRetriesAndDeadLettered() {
            var connection = TestDatabases.rabbitMQ();
            var attempts = new AtomicInteger();
            messageBus.subscribe(queue, ShoppingCartExternalEvent.ShoppingCartCheckedOut.class, event -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("Nobody can take the checkout");
            });

            try (var queues = new RabbitMQQueues(connection)) {
                messageBus.publish(List.of(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                        ShoppingCart.Id.of(UUID.randomUUID(), 1),
                        UUID.randomUUID(),
                        new PricedProductItem[0],
                        0,
                        OffsetDateTime.now())));

                assertThat(queues.receiveAll(queue + "-dlq", 1)).hasSize(1);
            } finally {
                new RabbitMQManagement(connection).deleteContaining(queue);
            }

            assertThat(attempts).hasValue(3);
            assertThat(meters.counter(
                                    RabbitMQMessageBus.DEAD_LETTERED,
                                    "message.type",
                                    ShoppingCartExternalEvent.ShoppingCartCheckedOut.class.getName(),
                                    "messaging.destination.name",
                                    queue)
                            .count())
                    .isEqualTo(1);
        }
    }

    @DirtiesContext
    @SpringBootTest(properties = {"messaging.bus=kafka", "messaging.retry.initial-interval=10ms"})
    @ActiveProfiles("in-memory")
    @AutoConfigureTracing
    @Import(InMemorySpansConfiguration.class)
    public static class KafkaDeadLettering {
        private static final String queue =
                "dead-lettering-" + UUID.randomUUID().toString().substring(0, 8);

        @TestConfiguration
        static class DeadLetteringQueue {
            @Bean
            static MessageQueue deadLetteringQueue() {
                return new MessageQueue(queue, ShoppingCartsConfig.external, "#");
            }
        }

        @DynamicPropertySource
        static void kafka(DynamicPropertyRegistry registry) {
            registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
        }

        @Autowired
        private MessageBus messageBus;

        @Autowired
        private Tracer tracer;

        @Autowired
        private InMemorySpanExporter spans;

        @Test
        public void deadLetterPublishHasASpanInTheTraceAndTheHandlerIsRetried() {
            var attempts = new AtomicInteger();
            messageBus.subscribe(queue, ShoppingCartExternalEvent.ShoppingCartCheckedOut.class, event -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("Nobody can take the checkout");
            });
            var publisherSpan = tracer.nextSpan().name("publisher").start();

            try (var _ = tracer.withSpan(publisherSpan)) {
                messageBus.publish(List.of(new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                        ShoppingCart.Id.of(UUID.randomUUID(), 1),
                        UUID.randomUUID(),
                        new PricedProductItem[0],
                        0,
                        OffsetDateTime.now())));
            } finally {
                publisherSpan.end();
            }

            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                            .anySatisfy(span -> {
                                assertThat(span.getName()).contains(queue + "-dlt");
                                assertThat(span.getSpanContext().getTraceId())
                                        .isEqualTo(publisherSpan.context().traceId());
                            }));
            assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        }
    }

    @DirtiesContext
    @SpringBootTest(properties = "eventstore.type=postgresql")
    @ActiveProfiles("in-memory")
    public static class PostgreSQLEventStoreWithProjectionsOnH2 {
        @DynamicPropertySource
        static void postgreSQL(DynamicPropertyRegistry registry) {
            var postgreSQL = TestDatabases.newPostgreSQLDatabase();

            TestDatabases.addPostgreSQLEventStore(registry, postgreSQL);
        }

        @Autowired
        private EventStore eventStore;

        @Autowired
        private Environment environment;

        @Test
        public void postgreSQLEventStoreRunsWithProjectionsOnH2() {
            var streamId = "ShoppingCart-%s".formatted(UUID.randomUUID());

            var result = eventStore.append(streamId, new Opened("cart"));

            assertThat(environment.getProperty("projections.database")).isEqualTo("h2");
            assertThat(eventStore).isInstanceOf(PostgreSQLEventStore.class);
            assertThat(result.succeeded()).isTrue();
        }

        record Opened(String cartId) {}
    }

    @SpringBootTest
    @ActiveProfiles("in-memory")
    public static class InMemoryProfile {
        @Autowired
        private MessageBus messageBus;

        @Autowired
        private EventStore eventStore;

        @Autowired
        private Environment environment;

        @Autowired
        private PendingOrderRepository pendingOrderRepository;

        @Autowired
        private DataSource dataSource;

        @Test
        public void inMemoryProfileSelectsTheInMemoryBusAndStore() {
            assertThat(environment.getProperty("messaging.bus")).isEqualTo("in-memory");
            assertThat(environment.getProperty("eventstore.type")).isEqualTo("in-memory");
            assertThat(messageBus).isInstanceOf(InMemoryMessageBus.class);
            assertThat(eventStore).isInstanceOf(InMemoryEventStore.class);
        }

        @Test
        public void inMemoryProfileStoresProjectionsInH2() throws SQLException {
            assertThat(environment.getProperty("projections.database")).isEqualTo("h2");
            var orderId = Order.Id.of(UUID.randomUUID());

            pendingOrderRepository.save(new PendingOrder(orderId));

            assertThat(pendingOrderRepository.findById(orderId)).isPresent();
            try (var connection = dataSource.getConnection()) {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            }
        }
    }

    @DirtiesContext
    @SpringBootTest
    public static class WithNoSettings {
        @DynamicPropertySource
        static void infrastructure(DynamicPropertyRegistry registry) {
            var postgreSQL = TestDatabases.newPostgreSQLDatabase();

            registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
            registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
            TestDatabases.addProjectionsDatabase(registry, postgreSQL);
        }

        @Autowired
        private MessageBus messageBus;

        @Autowired
        private EventStore eventStore;

        @Autowired
        private DataSource dataSource;

        @Test
        public void withNoSettingsTheApplicationUsesKafkaKurrentDBAndPostgreSQL() throws SQLException {
            assertThat(messageBus).isInstanceOf(KafkaMessageBus.class);
            assertThat(eventStore).isInstanceOf(KurrentDBEventStore.class);
            try (var connection = dataSource.getConnection()) {
                assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            }
        }
    }

    public static class DockerComposeProfiles {
        @Configuration
        static class NoBeans {}

        private static String composeProfilesWith(String... arguments) {
            try (var context = new SpringApplicationBuilder(NoBeans.class)
                    .web(WebApplicationType.NONE)
                    .run(arguments)) {
                return context.getEnvironment().getProperty("spring.docker.compose.profiles.active");
            }
        }

        @Test
        public void kafkaBusStartsTheSampleServicesOnly() {
            assertThat(composeProfilesWith("--messaging.bus=kafka")).isEqualTo("sample,kafka");
        }

        @Test
        public void rabbitMQBusAddsTheRabbitMQComposeProfile() {
            assertThat(composeProfilesWith("--messaging.bus=rabbitmq")).isEqualTo("sample,rabbitmq");
        }

        @Test
        public void inMemoryProfileStartsWithoutDockerCompose() {
            var application = new SpringApplicationBuilder(Application.class)
                    .web(WebApplicationType.NONE)
                    .profiles("in-memory")
                    .properties("spring.docker.compose.skip.in-tests=false");

            try (var context = application.run()) {
                assertThat(context.getEnvironment().getProperty("spring.docker.compose.enabled"))
                        .isEqualTo("false");
                assertThat(context.getBean(MessageBus.class)).isInstanceOf(InMemoryMessageBus.class);
            }
        }
    }
}
