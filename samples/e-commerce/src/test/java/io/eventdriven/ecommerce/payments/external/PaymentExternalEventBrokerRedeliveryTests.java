package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
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
public abstract class PaymentExternalEventBrokerRedeliveryTests {
    protected final String responses =
            "payments-responses-" + UUID.randomUUID().toString().substring(0, 8);
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final OffsetDateTime now = OffsetDateTime.now();

    @Autowired
    protected MessageContext messageContext;

    protected abstract MessageBus openBus(List<MessageRoutes> routes, List<MessageChannel> channels);

    protected abstract List<String> awaitPublishedMessageIds(int count);

    protected abstract void closeBusAndDeleteChannels();

    @AfterEach
    void cleanUp() {
        closeBusAndDeleteChannels();
    }

    @Test
    public void forwarderRepublishesARedeliveredEventWithTheSameMessageId() {
        var eventStore = new InMemoryEventStore(messageContext, ECommerceMessages.serializer());
        var messageBus = openBus(
                List.of(new MessageRoutes()
                        .route(
                                PaymentExternalEvent.class,
                                event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId())))),
                List.of(new MessageChannel(responses)));
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
                streamId,
                ExpectedStreamVersion.any(),
                new PaymentEvent.PaymentAuthorized(paymentId, now, now.plusDays(7)));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Trigger());

        var messageIds = awaitPublishedMessageIds(2);
        assertThat(messageIds).hasSize(2).containsOnly(messageIds.getFirst());
    }

    record Trigger() {}
}
