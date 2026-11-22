package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.ecommerce.payments.PaymentFacade;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PaymentExternalEventForwarderTests {
    private static final Duration paymentTimeout = Duration.ofMinutes(5);

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();

    @Test
    public void forwarderRefusesAPaymentWithoutSummarySoTheStoreDeliversItAgain() {
        var summaries = new PaymentSummaries(H2.bean(PaymentSummaryRepository.class));
        var bus = new InMemoryMessageBus(
                TestTracing.untraced(),
                List.of(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), "published")),
                List.of(new MessageChannel("published")),
                List.of());
        var published = new MessageCatcher();
        bus.use(envelope -> published.catchMessage(envelope.message()));
        var forwarder = new PaymentExternalEventForwarder(summaries, bus);
        var streamId = PaymentFacade.mapToStreamId(paymentId);
        var timedOut =
                new MessageEnvelope<>(new PaymentEvent.PaymentTimedOut(paymentId, now), Metadata.at(streamId, 1));

        assertThatThrownBy(() -> forwarder.onTimedOut(timedOut)).isInstanceOf(IllegalStateException.class);
        published.shouldNotReceiveAnyEvent();

        summaries.handle(new MessageEnvelope<>(
                new PaymentEvent.PaymentAuthorizationRequested(
                        paymentId, referenceId, amount, now, now.plus(paymentTimeout)),
                Metadata.at(streamId, 0)));
        forwarder.onTimedOut(timedOut);

        published.shouldReceiveMessages(new PaymentFailed(referenceId, paymentId, now));
    }
}
