package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.CancelPayment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.CapturePayment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.testing.FailingFirstSendKafkaTemplate;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaMessageBusTests extends BrokerMessageBusTests {

    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();

    @Autowired
    private ObservationRegistry observations;

    @Override
    protected MessageBus createBus(
            List<MessageRoutes> routes, List<MessageChannel> channels, List<MessageQueue> queues) {
        return KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                routes,
                channels,
                queues);
    }

    @Override
    protected Supplier<PublishedMessage> collectFirstPublishedTo(String channel) {
        return () -> {
            var record = recordsOf(channel, 1).getFirst();

            return new PublishedMessage(
                    KafkaTopics.header(record, KafkaMessageBus.TYPE_ID),
                    KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID),
                    KafkaTopics.header(record, "correlationId"),
                    KafkaTopics.header(record, "causationId"),
                    KafkaTopics.header(record, "traceparent"));
        };
    }

    @Override
    protected void sendRaw(String typeId, byte[] body) {
        var record = new ProducerRecord<String, byte[]>(orders, KEY, body);
        if (typeId != null) record.headers().add(KafkaMessageBus.TYPE_ID, typeId.getBytes(StandardCharsets.UTF_8));

        publishRaw(record);
    }

    @Override
    protected void beforePublish(MessageBus bus) {
        start((KafkaMessageBus) bus);
    }

    @Override
    protected void dispose(MessageBus bus) {
        ((KafkaMessageBus) bus).close();
        KafkaTopics.deleteContaining(bootstrapServers, id);
    }

    @Test
    public void everyDeclaredChannelIsATopicWithThreePartitionsAndOneReplica() {
        try (var admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() -> {
                var description = admin.describeTopics(List.of(orders))
                        .allTopicNames()
                        .get()
                        .get(orders);

                assertThat(description.partitions())
                        .hasSize(3)
                        .allSatisfy(
                                partition -> assertThat(partition.replicas()).hasSize(1));
            });
        }
    }

    @Test
    public void producerTimeoutsFitInsideThePublishTimeout() {
        var properties = KafkaMessageBus.producerFactory(bootstrapServers, TestKafka.PUBLISH_TIMEOUT)
                .getConfigurationProperties();

        var blockAndDeliver = Duration.ofMillis((long) properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG))
                .plusMillis((int) properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG));
        assertThat(blockAndDeliver).isLessThan(TestKafka.PUBLISH_TIMEOUT);
        assertThat((int) properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG))
                .isGreaterThanOrEqualTo((int) properties.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG));
    }

    @Test
    public void brokerDoesNotCreateATopicThatAProducerSendsToWithoutDeclaringIt() {
        var undeclared = "undeclared-" + id;
        var producerFactory = new DefaultKafkaProducerFactory<String, byte[]>(
                Map.of(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ProducerConfig.MAX_BLOCK_MS_CONFIG,
                        3000),
                new StringSerializer(),
                new ByteArraySerializer());
        var template = new KafkaTemplate<>(producerFactory);

        assertThatThrownBy(() -> template.send(undeclared, KEY, new byte[0]).get())
                .isInstanceOf(KafkaException.class);

        template.destroy();
        assertThat(KafkaTopics.namesContaining(bootstrapServers, undeclared)).isEmpty();
    }

    @Test
    public void publishToAnUndeclaredChannelFailsAndCreatesNoTopic() {
        var undeclared = "undeclared-" + id;
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(OrderPlaced.class, event -> undeclared)), List.of(), List.of());

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(undeclared);

        assertThat(KafkaTopics.namesContaining(bootstrapServers, undeclared)).isEmpty();
    }

    @Test
    public void newConsumerGroupReadsFromTheEarliestOffset() {
        publish(messageBus, new OrderPlaced("order-1"));
        var late = "late-" + id;
        var lateBus = newBus(
                List.of(ordersRoutes()),
                List.of(new MessageChannel(orders)),
                List.of(new MessageQueue(late, orders, "#")));
        lateBus.subscribe(late, OrderPlaced.class, messages::catchMessage);
        start((KafkaMessageBus) lateBus);

        awaitCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void aQueueBoundToAChannelDeclaredByALaterModuleGetsItsMessages() {
        var referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
        var authorized = new PaymentExternalEvent.PaymentAuthorized(
                referenceId, PaymentId.derivedFrom(referenceId), OffsetDateTime.now(ZoneOffset.UTC));
        var bus = newBus(
                List.of(PaymentsConfig.paymentsRoutes()),
                List.of(
                        PaymentsConfig.paymentsRequestsChannel(),
                        PaymentsConfig.paymentsResponsesChannel(),
                        PaymentsConfig.paymentsInternalChannel()),
                List.of(OrdersConfig.ordersPaymentsResponsesQueue()));
        bus.subscribe(
                OrdersConfig.paymentsResponses, PaymentExternalEvent.PaymentAuthorized.class, messages::catchMessage);

        publish(bus, authorized);

        awaitCaughtUp();
        assertThat(messages.published).contains(authorized);
    }

    @Test
    public void waitingForCatchUpFailsNamingTheGroupWhenAHandlerDoesNotFinish() {
        var release = new CountDownLatch(1);
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        publish(messageBus, new OrderPlaced("order-1"));

        try {
            assertThatThrownBy(() -> messageBus.awaitCaughtUp(Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(orders);
        } finally {
            release.countDown();
        }
    }

    @Test
    public void twoMessagesAboutOneEntityShareTheRecordKeyAndPartition() {
        publish(messageBus, new OrderPlaced("order-1"), new ConfirmOrder("order-1"));

        var records = recordsOf(orders, 2);

        assertThat(records).extracting(record -> record.key()).containsOnly(KEY);
        assertThat(records)
                .extracting(record -> record.partition())
                .containsOnly(records.getFirst().partition());
    }

    @Test
    public void twoMessagesAboutOnePaymentShareTheRecordKeyAndPartition() {
        var paymentId = PaymentId.of(UUID.randomUUID());
        var bus = newBus(
                List.of(PaymentsConfig.paymentsRoutes()),
                List.of(
                        PaymentsConfig.paymentsRequestsChannel(),
                        PaymentsConfig.paymentsResponsesChannel(),
                        PaymentsConfig.paymentsInternalChannel()),
                List.of());

        publish(bus, new CapturePayment(paymentId), new CancelPayment(paymentId));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var records = KafkaTopics.recordsOf(bootstrapServers, PaymentsConfig.requests).stream()
                    .filter(record -> Urns.routingKeyOf(paymentId.value()).equals(record.key()))
                    .toList();

            assertThat(records).hasSize(2);
            assertThat(records)
                    .extracting(record -> record.partition())
                    .containsOnly(records.getFirst().partition());
        });
    }

    @Test
    public void routingConsumerKeepsKeyTypeHeadersAndBody() {
        var responses = "responses-" + id;
        var queue = "orders-responses-" + id;
        var bus = routedBus(responses, queue, null);

        publish(bus, new OrderPlaced("ecommerce.order.9"));

        var original = recordsOf(responses, 1).getFirst();
        var copy = recordsOf(queue, 1).getFirst();
        assertThat(copy.key()).isEqualTo(original.key());
        assertThat(copy.value()).isEqualTo(original.value());
        assertThat(headersOf(copy))
                .isEqualTo(headersOf(original))
                .containsKeys(KafkaMessageBus.TYPE_ID, KafkaMessageBus.MESSAGE_ID);
    }

    @Test
    public void routingConsumerWritesAResponseAfterAFailedSendBeforeTheCommit() {
        var responses = "responses-" + id;
        var queue = "orders-responses-" + id;
        var failingFirstSend = new FailingFirstSendKafkaTemplate(
                KafkaMessageBus.producerFactory(bootstrapServers, TestKafka.PUBLISH_TIMEOUT));
        var bus = routedBus(responses, queue, failingFirstSend);

        publish(bus, new OrderPlaced("ecommerce.order.9"));

        awaitCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("ecommerce.order.9"));
        assertThat(recordsOf(queue, 1)).hasSize(1);
    }

    @Test
    public void publishOnAnInterruptedThreadFailsWithAnInterrupt() {
        start((KafkaMessageBus) messageBus);
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> messageBus.publish(List.of(new OrderPlaced("order-1"))))
                    .isInstanceOf(InterruptException.class);
        } finally {
            Thread.interrupted();
        }
    }

    private MessageBus routedBus(String responses, String queue, KafkaTemplate<String, byte[]> routingTemplate) {
        var routes = List.of(
                new MessageRoutes().route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id())));
        var channels = List.of(new MessageChannel(responses));
        var queues = List.of(new MessageQueue(queue, responses, "ecommerce.order.*"));
        var bus = routingTemplate == null
                ? newBus(routes, channels, queues)
                : track(new KafkaMessageBus(
                        bootstrapServers,
                        messageContext,
                        observations,
                        serializer(),
                        TestKafka.PUBLISH_TIMEOUT,
                        TestKafka.failureHandling(bootstrapServers, observations),
                        routingTemplate,
                        routes,
                        channels,
                        queues));

        return bus.subscribe(queue, OrderPlaced.class, messages::catchMessage);
    }

    private void start(KafkaMessageBus bus) {
        if (!bus.isRunning()) bus.start();
    }

    private void publishRaw(ProducerRecord<String, byte[]> record) {
        var template =
                new KafkaTemplate<>(KafkaMessageBus.producerFactory(bootstrapServers, TestKafka.PUBLISH_TIMEOUT));
        template.send(record).join();
        template.destroy();
    }

    private List<ConsumerRecord<String, byte[]>> recordsOf(String topic, int count) {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> KafkaTopics.recordsOf(bootstrapServers, topic).size() >= count);

        return KafkaTopics.recordsOf(bootstrapServers, topic);
    }

    private Map<String, String> headersOf(ConsumerRecord<String, byte[]> record) {
        var headers = new TreeMap<String, String>();
        record.headers()
                .forEach(header -> headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
        return headers;
    }
}
