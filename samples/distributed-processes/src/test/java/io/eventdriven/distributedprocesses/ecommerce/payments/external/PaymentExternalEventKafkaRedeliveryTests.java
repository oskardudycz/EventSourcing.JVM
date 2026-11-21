package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.ecommerce.ECommerceMessages;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.kurrent.dbclient.StreamState;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
public class PaymentExternalEventKafkaRedeliveryTests {
    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();
    private final String responses =
            "payments-responses-" + UUID.randomUUID().toString().substring(0, 8);
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final OffsetDateTime now = OffsetDateTime.now();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    private KafkaMessageBus messageBus;

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
        KafkaTopics.deleteContaining(bootstrapServers, responses);
    }

    @Test
    public void forwarderRepublishesARedeliveredEventWithTheSameMessageId() {
        var eventStore = new InMemoryEventStore(messageContext);
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                TestMessages.serializer(ECommerceMessages.mapping()),
                Duration.ofSeconds(10),
                TestKafka.failureHandling(bootstrapServers, observations));
        messageBus
                .routes(new MessageRoutes()
                        .route(
                                PaymentExternalEvent.class,
                                event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId()))))
                .channel(responses);
        var summaries = new PaymentSummaries(H2.bean(PaymentSummaryRepository.class));
        var forwarder = new PaymentExternalEventForwarder(summaries, messageBus);
        var calls = new AtomicInteger();
        eventStore.subscribe("payment-summaries", summaries.canHandle(), summaries::handle);
        eventStore.subscribe("payments-external-forwarder", List.of(PaymentEvent.PaymentAuthorized.class), envelope -> {
            forwarder.handle(envelope);

            if (calls.incrementAndGet() == 1) throw new IllegalStateException("The process crashed after publishing");
        });

        var streamId = "Payment-" + UUID.randomUUID();
        eventStore.append(
                streamId,
                new PaymentEvent.PaymentAuthorizationRequested(paymentId, referenceId, 62.5, now, now.plusMinutes(5)));
        eventStore.append(
                streamId, StreamState.any(), new PaymentEvent.PaymentAuthorized(paymentId, now, now.plusDays(7)));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Trigger());

        await().atMost(Duration.ofSeconds(30))
                .until(() -> KafkaTopics.recordsOf(bootstrapServers, responses).size() >= 2);
        var messageIds = KafkaTopics.recordsOf(bootstrapServers, responses).stream()
                .map(record -> KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID))
                .toList();
        assertThat(messageIds).hasSize(2).containsOnly(messageIds.getFirst());
    }

    record Trigger() {}
}
