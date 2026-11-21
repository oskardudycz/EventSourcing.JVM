package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.testing.TestTracing;
import io.kurrent.dbclient.StreamState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class PaymentExternalEventRedeliveryTests {
    private final TestTracing tracing = new TestTracing();
    private final InMemoryEventStore eventStore = new InMemoryEventStore(tracing.messageContext());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(tracing.messageContext());
    private final AtomicInteger calls = new AtomicInteger();

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final String receivedStream = "Probe-received-" + UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    public PaymentExternalEventRedeliveryTests() {
        PaymentsConfig.configure(
                eventStore,
                messageBus,
                new SilentPaymentGateway(),
                PaymentValidity.of(Duration.ofMinutes(5)),
                H2.bean(PaymentSummaryRepository.class),
                H2.bean(PendingPaymentRepository.class),
                H2.bean(AuthorizedPaymentRepository.class),
                () -> now);
        messageBus
                .bind("probe", PaymentsConfig.responses, "#")
                .subscribe("probe", PaymentExternalEvent.PaymentAuthorized.class, event -> {
                    eventStore.append(
                            receivedStream,
                            StreamState.any(),
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
