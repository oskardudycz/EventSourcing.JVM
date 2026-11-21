package io.eventdriven.distributedprocesses.core.messaging;

import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.CancelPayment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.CapturePayment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.FailingFirstSendKafkaTemplate;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.RouteRecordingMessageBus;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class KafkaMessageBusTests extends MessageBusTests {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    private static final String KEY = "ecommerce.order.1";

    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    @Autowired
    private Tracer tracer;

    @Autowired
    private InMemorySpanExporter spans;

    @Override
    protected MessageBus createBus() {
        return KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations));
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
        var properties = KafkaMessageBus.producerFactory(bootstrapServers, PUBLISH_TIMEOUT)
                .getConfigurationProperties();

        var blockAndDeliver = Duration.ofMillis((long) properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG))
                .plusMillis((int) properties.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG));
        assertThat(blockAndDeliver).isLessThan(PUBLISH_TIMEOUT);
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
        var bus = newBus().routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> undeclared));

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(undeclared);

        assertThat(KafkaTopics.namesContaining(bootstrapServers, undeclared)).isEmpty();
    }

    @Test
    public void newConsumerGroupReadsFromTheEarliestOffset() {
        publish(messageBus, new OrderPlaced("order-1"));
        await().atMost(Duration.ofSeconds(30))
                .until(() -> !KafkaTopics.recordsOf(bootstrapServers, orders).isEmpty());
        var late = "late-" + id;

        messageBus.bind(late, orders, "#");
        messageBus.subscribe(late, OrderPlaced.class, event -> log.add("late:" + event.id()));
        start((KafkaMessageBus) messageBus);

        awaitLog("late:order-1");
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
        var recording = new RouteRecordingMessageBus();
        PaymentsConfig.configure(
                new InMemoryEventStore(messageContext),
                recording,
                new SilentPaymentGateway(),
                PaymentValidity.of(Duration.ofMinutes(5)),
                H2.bean(PaymentSummaryRepository.class),
                H2.bean(PendingPaymentRepository.class),
                H2.bean(AuthorizedPaymentRepository.class),
                OffsetDateTime::now);
        var bus = newBus();
        recording.routes.forEach(bus::routes);
        recording.channels.forEach(bus::channel);

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
        var bus = routedBus(responses, queue, newBus());

        publish(bus, new OrderPlaced("ecommerce.order.9"));

        var original = recordsOf(responses, 1).getFirst();
        var copy = recordsOf(queue, 1).getFirst();
        assertThat(copy.key()).isEqualTo(original.key());
        assertThat(copy.value()).isEqualTo(original.value());
        assertThat(headersOf(copy)).isEqualTo(headersOf(original)).containsKeys("messageType", "messageId");
    }

    @Test
    public void routingConsumerWritesAResponseAfterAFailedSendBeforeTheCommit() {
        var responses = "responses-" + id;
        var queue = "orders-responses-" + id;
        var failingFirstSend =
                new FailingFirstSendKafkaTemplate(KafkaMessageBus.producerFactory(bootstrapServers, PUBLISH_TIMEOUT));
        var bus = routedBus(responses, queue, failingFirstSendBus(failingFirstSend));

        publish(bus, new OrderPlaced("ecommerce.order.9"));

        awaitLog("orders:ecommerce.order.9");
        assertThat(recordsOf(queue, 1)).hasSize(1);
    }

    @Test
    public void consumerMapsTheStableTypeNameToTheCurrentClass() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed:" + event.id()));
        var record = new ProducerRecord<String, byte[]>(
                orders, KEY, "{\"id\":\"order-1\"}".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaMessageBus.MESSAGE_TYPE, "test.OrderPlaced".getBytes(StandardCharsets.UTF_8));

        publishRaw(record);
        start((KafkaMessageBus) messageBus);

        awaitLog("placed:order-1");
    }

    @Test
    public void publishedMessageIsWrittenUnderItsStableTypeName() {
        publish(messageBus, new OrderPlaced("order-1"));

        var record = recordsOf(orders, 1).getFirst();

        assertThat(KafkaTopics.header(record, KafkaMessageBus.MESSAGE_TYPE)).isEqualTo("test.OrderPlaced");
    }

    @Test
    public void messageStartingAProcessCarriesAMessageIdAndAGeneratedCorrelationId() {
        publish(messageBus, new OrderPlaced("order-1"));

        var record = recordsOf(orders, 1).getFirst();

        var correlationId = KafkaTopics.header(record, "correlationId");
        assertThat(UUID.fromString(KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID)))
                .isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(KafkaTopics.header(record, "causationId")).isEqualTo(correlationId);
        assertThat(KafkaTopics.header(record, "traceparent")).isNotBlank();
    }

    @Test
    public void messagePublishedWhileHandlingAnotherCarriesItsCorrelationAndCausationIds() {
        try (var _ = messageContext.handling("handled-message-id", "process-correlation-id")) {
            publish(messageBus, new OrderPlaced("order-1"));
        }

        var record = recordsOf(orders, 1).getFirst();

        assertThat(KafkaTopics.header(record, "correlationId")).isEqualTo("process-correlation-id");
        assertThat(KafkaTopics.header(record, "causationId")).isEqualTo("handled-message-id");
        assertThat(KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID)).isNotEqualTo("handled-message-id");
    }

    @Test
    public void handlerRunsInTheTraceOfThePublishedMessageWithAllItsValues() {
        var seen = new CopyOnWriteArrayList<String>();
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            var context = tracer.currentSpan().context();
            seen.add(tracer.getBaggage("correlationId").get());
            seen.add(tracer.getBaggage("causationId").get());
            seen.add(context.traceId());
            seen.add(context.spanId());
        });
        var publisherSpan = tracer.nextSpan().name("publisher").start();

        try (var _ = tracer.withSpan(publisherSpan);
                var _ = messageContext.handling("handled-message-id", "process-correlation-id")) {
            publish(messageBus, new OrderPlaced("order-1"));
        } finally {
            publisherSpan.end();
        }

        var messageId = KafkaTopics.header(recordsOf(orders, 1).getFirst(), KafkaMessageBus.MESSAGE_ID);
        await().atMost(Duration.ofSeconds(30)).until(() -> seen.size() == 4);
        assertThat(seen.subList(0, 3))
                .containsExactly(
                        "process-correlation-id",
                        messageId,
                        publisherSpan.context().traceId());
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems()).anySatisfy(span -> {
                    assertThat(span.getName()).isEqualTo("handle OrderPlaced");
                    assertThat(span.getSpanContext().getSpanId()).isEqualTo(seen.get(3));
                    assertThat(span.getSpanContext().getTraceId()).isEqualTo(seen.get(2));
                }));
    }

    @Test
    public void recordWithoutATypeHeaderIsSkippedAndTheNextMessageIsHandled() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed:" + event.id()));
        publishRaw(new ProducerRecord<>(orders, KEY, "{}".getBytes(StandardCharsets.UTF_8)));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLog("placed:order-1");
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

    @Test
    public void consumerOpensAStepSpanNamedAfterTheMessageType() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLog("placed");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .extracting(span -> span.getName())
                        .contains("handle OrderPlaced"));
    }

    private MessageBus routedBus(String responses, String queue, MessageBus bus) {
        return bus.routes(
                        new MessageRoutes().route(OrderPlaced.class, event -> new RoutingTarget(responses, event.id())))
                .channel(responses)
                .bind(queue, responses, "ecommerce.order.*")
                .subscribe(queue, OrderPlaced.class, event -> log.add("orders:" + event.id()));
    }

    private MessageBus failingFirstSendBus(KafkaTemplate<String, byte[]> routingTemplate) {
        return track(new KafkaMessageBus(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                routingTemplate));
    }

    private void start(KafkaMessageBus bus) {
        if (!bus.isRunning()) bus.start();
    }

    private void publishRaw(ProducerRecord<String, byte[]> record) {
        var template = new KafkaTemplate<>(KafkaMessageBus.producerFactory(bootstrapServers, PUBLISH_TIMEOUT));
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

    private static MessageSerializer serializer() {
        return TestMessages.serializer(messageMapping()
                .register("payments.CapturePayment", CapturePayment.class)
                .register("payments.CancelPayment", CancelPayment.class));
    }
}
