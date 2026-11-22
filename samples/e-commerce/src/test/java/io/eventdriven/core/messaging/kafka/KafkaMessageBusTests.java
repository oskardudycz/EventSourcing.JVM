package io.eventdriven.core.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.BrokerMessageBusTests;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.messaging.kafka.FailingFirstSendKafkaTemplate;
import io.eventdriven.testing.messaging.kafka.KafkaTopics;
import io.eventdriven.testing.messaging.kafka.TestKafka;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        var bus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                routes,
                channels,
                queues);
        bus.start();

        return bus;
    }

    @Override
    protected Supplier<List<PublishedMessage>> collectPublishedTo(String channel, int count) {
        return () -> recordsOf(channel, count).stream()
                .map(record -> new PublishedMessage(
                        KafkaTopics.header(record, KafkaMessageBus.TYPE_ID),
                        KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID),
                        KafkaTopics.header(record, "correlationId"),
                        KafkaTopics.header(record, "causationId"),
                        KafkaTopics.header(record, "traceparent")))
                .toList();
    }

    @Override
    protected void sendRaw(String typeId, byte[] body) {
        var record = new ProducerRecord<String, byte[]>(orders, KEY, body);
        if (typeId != null) record.headers().add(KafkaMessageBus.TYPE_ID, typeId.getBytes(StandardCharsets.UTF_8));

        publishRaw(record);
    }

    @Override
    protected void dispose(MessageBus bus) {
        ((KafkaMessageBus) bus).close();
        KafkaTopics.deleteContaining(bootstrapServers, id);
    }

    @Test
    public void publishToAChannelWithNoConsumerGroupSucceedsAndKeepsTheMessages() {
        var responses = "payments-" + id + "-responses";
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(OrderPlaced.class, event -> responses)),
                List.of(new MessageChannel(responses)),
                List.of());

        bus.publish(List.of(new OrderPlaced("order-1"), new OrderPlaced("order-2")));

        assertThat(recordsOf(responses, 2)).hasSize(2);
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

        assertThatThrownBy(() -> bus.publish(List.of(new OrderPlaced("order-1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(undeclared);

        assertThat(KafkaTopics.namesContaining(bootstrapServers, undeclared)).isEmpty();
    }

    @Test
    public void newConsumerGroupReadsFromTheEarliestOffset() {
        messageBus.publish(List.of(new OrderPlaced("order-1")));
        var late = "late-" + id;
        var lateBus = newBus(
                List.of(ordersRoutes()),
                List.of(new MessageChannel(orders)),
                List.of(new MessageQueue(late, orders, "#")));
        lateBus.subscribe(late, OrderPlaced.class, messages::catchMessage);

        lateBus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
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
        messageBus.publish(List.of(new OrderPlaced("order-1")));

        try {
            assertThatThrownBy(() -> messageBus.whenCaughtUp(Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(orders);
        } finally {
            release.countDown();
        }
    }

    @Test
    public void twoMessagesAboutOneEntityShareTheRecordKeyAndPartition() {
        messageBus.publish(List.of(new OrderPlaced("order-1"), new ConfirmOrder("order-1")));

        var records = recordsOf(orders, 2);

        assertThat(records).extracting(record -> record.key()).containsOnly(KEY);
        assertThat(records)
                .extracting(record -> record.partition())
                .containsOnly(records.getFirst().partition());
    }

    @Test
    public void twoMessagesAboutOnePaymentShareTheRecordKeyAndPartition() {
        var requests = "payments-requests-" + id;
        var paymentId = Urns.of("ecommerce", "payment", UUID.randomUUID());
        var bus = newBus(
                List.of(new MessageRoutes()
                        .route(
                                PaymentCommand.class,
                                command -> new RoutingTarget(requests, Urns.routingKeyOf(command.paymentId())))),
                List.of(new MessageChannel(requests)),
                List.of());

        bus.publish(List.of(new PaymentCommand.CapturePayment(paymentId), new PaymentCommand.CancelPayment(paymentId)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var records = KafkaTopics.recordsOf(bootstrapServers, requests).stream()
                    .filter(record -> Urns.routingKeyOf(paymentId).equals(record.key()))
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

        bus.publish(List.of(new OrderPlaced("ecommerce.order.9")));

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

        bus.publish(List.of(new OrderPlaced("ecommerce.order.9")));

        bus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("ecommerce.order.9"));
        assertThat(recordsOf(queue, 1)).hasSize(1);
    }

    @Test
    public void publishOnAnInterruptedThreadFailsWithAnInterrupt() {
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
        if (routingTemplate == null)
            return newBus(routes, channels, queues).subscribe(queue, OrderPlaced.class, messages::catchMessage);

        var bus = track(new KafkaMessageBus(
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
        bus.start();

        return bus.subscribe(queue, OrderPlaced.class, messages::catchMessage);
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
