package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.EventMetadata;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class PaymentExternalEventRedeliveryTests {
    private final TestTracing tracing = new TestTracing();
    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(tracing.messageContext(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            tracing.messageContext(),
            List.of(PaymentsConfig.paymentsRoutes()),
            List.of(
                    PaymentsConfig.paymentsRequestsChannel(),
                    PaymentsConfig.paymentsResponsesChannel(),
                    PaymentsConfig.paymentsInternalChannel()),
            List.of(
                    PaymentsConfig.paymentsRequestsQueue(),
                    PaymentsConfig.paymentsInternalQueue(),
                    new MessageQueue("probe", PaymentsConfig.responses, "#")));
    private final AtomicInteger calls = new AtomicInteger();

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final String receivedStream = "Probe-received-" + UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    public PaymentExternalEventRedeliveryTests() {
        var paymentGateway = new SilentPaymentGateway();
        var paymentsConfig = new PaymentsConfig();
        var paymentSummaries = paymentsConfig.paymentSummaries(eventStore, H2.bean(PaymentSummaryRepository.class));
        var pendingPayments = paymentsConfig.pendingPayments(eventStore, H2.bean(PendingPaymentRepository.class));
        var authorizedPayments =
                paymentsConfig.authorizedPayments(eventStore, H2.bean(AuthorizedPaymentRepository.class));
        paymentsConfig.paymentExternalEventForwarder(eventStore, paymentSummaries, messageBus);
        paymentsConfig.captureGatewayClient(eventStore, paymentGateway, messageBus);
        paymentsConfig.authorizationGatewayClient(eventStore, paymentGateway);
        paymentsConfig.paymentFacade(
                eventStore,
                tracing.messageContext(),
                messageBus,
                paymentGateway,
                Payment.Validity.of(Duration.ofMinutes(5)),
                () -> now);
        messageBus.subscribe("probe", PaymentExternalEvent.PaymentAuthorized.class, event -> {
            eventStore.append(
                    receivedStream,
                    ExpectedStreamVersion.any(),
                    new Received(event.paymentId().value()));

            if (calls.incrementAndGet() == 1) throw new IllegalStateException("Consumer is not ready");
        });
    }

    @Test
    public void forwarderRepublishesARedeliveredEventWithTheSameMessageId() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, 62.5)));
        messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, now.plusDays(7))));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Received("trigger"));

        var result = (EventStore.ReadResult.Success) eventStore.read(receivedStream);
        var causations = result.events().stream()
                .map(EventEnvelope::metadata)
                .map(EventMetadata::causationId)
                .toList();

        assertThat(causations).hasSize(2).containsOnly(causations.getFirst());
    }

    record Received(String about) {}
}
