package io.eventdriven.core.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.messaging.kafka.FailingFirstSendKafkaTemplate;
import io.eventdriven.testing.messaging.kafka.KafkaTopics;
import io.eventdriven.testing.messaging.kafka.TestKafka;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class KafkaMessageBusFailureTests {
    public record PaymentReceived(String id) {}

    public record RoomInspected(String room, String inspector) {}

    private static final String KEY = "ecommerce.payment.1";

    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();
    private final String id = UUID.randomUUID().toString().substring(0, 8);
    private final String payments = "shopping_carts-external-" + id;
    private final String billing = "orders-shopping_carts-external-" + id;
    private final String shipping = "shipping-" + id;
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MessageCatcher messages = new MessageCatcher();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    @Autowired
    private Tracer tracer;

    @Autowired
    private InMemorySpanExporter spans;

    private KafkaMessageBus messageBus;

    @BeforeEach
    void createMessageBus() {
        messageBus = busWith(new MessageQueue(billing, payments, "#"));
    }

    private KafkaMessageBus busWith(MessageQueue... queues) {
        var bus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations, meters, TestKafka.PUBLISH_TIMEOUT),
                List.of(paymentRoutes()),
                List.of(new MessageChannel(payments)),
                List.of(queues));
        bus.start();

        return bus;
    }

    private MessageRoutes paymentRoutes() {
        return new MessageRoutes()
                .routeTarget(MessageRoutes.inPackage(getClass().getPackageName()), payments)
                .routeKey(PaymentReceived.class, message -> KEY)
                .routeKey(RoomInspected.class, message -> KEY);
    }

    private static MessageSerializer serializer() {
        return TestMessages.serializer(new MessageMapping()
                .trust(PaymentReceived.class.getPackageName())
                .register("test.PaymentReceived", PaymentReceived.class)
                .register("test.RoomInspected", RoomInspected.class)
                .register(
                        "test.RoomInspected.v1",
                        String.class,
                        RoomInspected.class,
                        room -> new RoomInspected(room, "unknown"))
                .register(
                        "test.RoomInspected.v0",
                        RoomInspected.class,
                        json -> new RoomInspected(json.get("r").asString(), "unknown")));
    }

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
        KafkaTopics.deleteContaining(bootstrapServers, id);
    }

    @Test
    public void handlerFailingOnceSucceedsOnTheSecondAttempt() {
        var attempts = new AtomicInteger();
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Payment gateway unavailable");
            messages.catchMessage(message);
        });

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        messageBus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new PaymentReceived("payment-1"));
        assertThat(attempts).hasValue(2);
        assertThat(KafkaTopics.recordsOf(bootstrapServers, billing + "-dlt")).isEmpty();
        assertThat(deadLettered()).isZero();
    }

    @Test
    public void aDeadLetteredMessageCountsAsCaughtUp() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            throw new IllegalStateException("Payment gateway unavailable");
        });

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        messageBus.whenCaughtUp();
        assertThat(KafkaTopics.recordsOf(bootstrapServers, billing + "-dlt")).hasSize(1);
    }

    @Test
    public void alwaysFailingHandlerSendsTheMessageToItsQueuesDeadLetterTopicAfterTheLastRetry() {
        var attempts = new AtomicInteger();
        messageBus.close();
        messageBus = busWith(new MessageQueue(billing, payments, "#"), new MessageQueue(shipping, payments, "#"));
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Payment gateway unavailable");
        });
        messageBus.subscribe(shipping, PaymentReceived.class, messages::catchMessage);

        messageBus.publish(List.of(new PaymentReceived("payment-1"), new PaymentReceived("payment-2")));

        var deadLetters = deadLettersOf(billing, 2);
        messages.shouldReceiveMessages(new PaymentReceived("payment-1"), new PaymentReceived("payment-2"));
        assertThat(attempts).hasValue(6);
        assertThat(deadLetters)
                .allSatisfy(record -> assertThat(deliveryAttemptOf(record)).isEqualTo(3));
        assertThat(deadLetters).extracting(ConsumerRecord::key).containsOnly(KEY);
        assertThat(KafkaTopics.recordsOf(bootstrapServers, shipping + "-dlt")).isEmpty();
        assertThat(deadLettered()).isEqualTo(2);
    }

    @Test
    public void deadLetterPublishHasASpanInTheTraceOfTheMessage() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            throw new IllegalStateException("Payment gateway unavailable");
        });
        var publisherSpan = tracer.nextSpan().name("publisher").start();

        try (var _ = tracer.withSpan(publisherSpan)) {
            messageBus.publish(List.of(new PaymentReceived("payment-1")));
        } finally {
            publisherSpan.end();
        }

        deadLettersOf(billing, 1);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems()).anySatisfy(span -> {
                    assertThat(span.getName()).contains(billing + "-dlt");
                    assertThat(span.getSpanContext().getTraceId())
                            .isEqualTo(publisherSpan.context().traceId());
                }));
    }

    @Test
    public void bodyThatIsNotJsonGoesToTheDeadLetterTopicWithoutRetriesAndTheNextMessageIsHandled() {
        messageBus.subscribe(billing, PaymentReceived.class, messages::catchMessage);
        publishRaw("test.PaymentReceived", "{\"id\":");

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        var deadLetters = deadLettersOf(billing, 1);
        messages.shouldReceiveSingleEvent(new PaymentReceived("payment-1"));
        assertThat(new String(deadLetters.getFirst().value(), StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":");
        assertThat(deliveryAttemptOf(deadLetters.getFirst())).isEqualTo(1);
        assertThat(deadLettered()).isEqualTo(1);
    }

    @Test
    public void typeOutsideTheSamplesPackageIsNotCreatedAndIsDeadLettered() {
        messageBus.subscribe(billing, PaymentReceived.class, messages::catchMessage);
        publishRaw("java.lang.ProcessBuilder", "{}");

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        var deadLetters = deadLettersOf(billing, 1);
        messages.shouldReceiveSingleEvent(new PaymentReceived("payment-1"));
        assertThat(KafkaTopics.header(deadLetters.getFirst(), KafkaMessageBus.TYPE_ID))
                .isEqualTo("java.lang.ProcessBuilder");
        assertThat(KafkaTopics.header(deadLetters.getFirst(), KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(UnreadableMessageException.class.getName());
        assertThat(deliveryAttemptOf(deadLetters.getFirst())).isEqualTo(1);
    }

    @Test
    public void aJavaUtilTypeIsDeadLetteredInsteadOfRead() {
        messageBus.subscribe(billing, PaymentReceived.class, messages::catchMessage);
        publishRaw("java.util.ArrayList", "[]");

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        deadLettersOf(billing, 1);
        messages.shouldReceiveSingleEvent(new PaymentReceived("payment-1"));
    }

    @Test
    public void deadLetterCountIsTaggedWithTheStableTypeNameAndTheQueue() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            throw new IllegalStateException("Payment gateway unavailable");
        });

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        deadLettersOf(billing, 1);
        assertThat(meters.find(KafkaMessageBus.DEAD_LETTERED)
                        .tag("message.type", "test.PaymentReceived")
                        .tag("messaging.destination.name", billing)
                        .counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
    }

    @Test
    public void deadLetterCountOfATypeThatIsNotRegisteredIsTaggedUnknown() {
        publishRaw(PaymentReceived.class.getPackageName() + ".NoSuchMessage", "{}");
        publishRaw("java.lang.ProcessBuilder", "{}");

        deadLettersOf(billing, 2);
        assertThat(meters.find(KafkaMessageBus.DEAD_LETTERED).counters())
                .extracting(counter -> counter.getId().getTag("message.type"))
                .containsExactly("unknown");
        assertThat(deadLettered()).isEqualTo(2);
    }

    @Test
    public void batchWithAMessageThatHasNoRoutePublishesNothing() {
        var bus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(bootstrapServers, observations),
                List.of(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(getClass().getPackageName()), payments)
                        .routeKey(PaymentReceived.class, message -> KEY)),
                List.of(new MessageChannel(payments)),
                List.of());

        try {
            assertThatThrownBy(() -> bus.publish(List.of(new PaymentReceived("payment-1"), new Object())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Object.class.getName());
        } finally {
            bus.close();
        }

        assertThat(KafkaTopics.recordsOf(bootstrapServers, payments)).isEmpty();
    }

    @Test
    public void typeNameInsideTheSamplesPackageThatNamesNoClassIsDeadLettered() {
        publishRaw(PaymentReceived.class.getPackageName() + ".NoSuchMessage", "{}");

        var deadLetters = deadLettersOf(billing, 1);

        assertThat(KafkaTopics.header(deadLetters.getFirst(), KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(UnreadableMessageException.class.getName());
    }

    @Test
    public void messageIsNotLostWhenTheFirstDeadLetterPublishFails() {
        messageBus.close();
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                serializer(),
                TestKafka.PUBLISH_TIMEOUT,
                TestKafka.failureHandling(
                        meters,
                        new FailingFirstSendKafkaTemplate(
                                KafkaMessageBus.producerFactory(bootstrapServers, TestKafka.PUBLISH_TIMEOUT))),
                List.of(paymentRoutes()),
                List.of(new MessageChannel(payments)),
                List.of(new MessageQueue(billing, payments, "#")));
        messageBus.start();
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            throw new IllegalStateException("Payment gateway unavailable");
        });

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        assertThat(deadLettersOf(billing, 1)).hasSize(1);
        assertThat(deadLettered()).isEqualTo(1);
    }

    @Test
    public void recordWithoutATypeHeaderIsDeadLettered() {
        messageBus.subscribe(billing, PaymentReceived.class, messages::catchMessage);
        publishRaw(new ProducerRecord<>(payments, KEY, "{}".getBytes(StandardCharsets.UTF_8)));

        messageBus.publish(List.of(new PaymentReceived("payment-1")));

        deadLettersOf(billing, 1);
        messages.shouldReceiveSingleEvent(new PaymentReceived("payment-1"));
    }

    @Test
    public void oldVersionOfAMessageIsTransformedToTheCurrentOne() {
        messageBus.subscribe(billing, RoomInspected.class, messages::catchMessage);
        publishRaw("test.RoomInspected.v1", "\"101\"");
        publishRaw("test.RoomInspected.v0", "{\"r\":\"102\"}");
        publishRaw("test.RoomInspected", "{\"room\":\"103\",\"inspector\":\"anna\"}");

        messageBus.whenCaughtUp();
        assertThat(messages.published)
                .containsExactlyInAnyOrder(
                        new RoomInspected("101", "unknown"),
                        new RoomInspected("102", "unknown"),
                        new RoomInspected("103", "anna"));
        assertThat(KafkaTopics.recordsOf(bootstrapServers, billing + "-dlt")).isEmpty();
    }

    private void publishRaw(String typeName, String body) {
        var record = new ProducerRecord<>(payments, KEY, body.getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaMessageBus.TYPE_ID, typeName.getBytes(StandardCharsets.UTF_8));
        publishRaw(record);
    }

    private void publishRaw(ProducerRecord<String, byte[]> record) {
        var template =
                new KafkaTemplate<>(KafkaMessageBus.producerFactory(bootstrapServers, TestKafka.PUBLISH_TIMEOUT));
        template.send(record).join();
        template.destroy();
    }

    private List<ConsumerRecord<String, byte[]>> deadLettersOf(String queue, int count) {
        messageBus.whenCaughtUp();

        var records = KafkaTopics.recordsOf(bootstrapServers, queue + "-dlt");
        assertThat(records).hasSize(count);

        return records;
    }

    private double deadLettered() {
        var counter = meters.find(KafkaMessageBus.DEAD_LETTERED).counter();

        return counter != null ? counter.count() : 0;
    }

    private static int deliveryAttemptOf(ConsumerRecord<String, byte[]> record) {
        return ByteBuffer.wrap(record.headers()
                        .lastHeader(KafkaHeaders.DELIVERY_ATTEMPT)
                        .value())
                .getInt();
    }
}
