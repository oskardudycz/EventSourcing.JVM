package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.ecommerce.payments.PaymentFacade;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.ecommerce.payments.authorization.PendingPaymentRepository;
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
    private static final Duration authorizationValidity = Duration.ofDays(7);

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = now.plus(authorizationValidity);
    private final OffsetDateTime timedOutAt = now.plusMinutes(5);

    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(TestTracing.untraced(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(PaymentsConfig.paymentsRoutes()),
            List.of(
                    PaymentsConfig.paymentsRequestsChannel(),
                    PaymentsConfig.paymentsResponsesChannel(),
                    PaymentsConfig.paymentsInternalChannel()),
            List.of(PaymentsConfig.paymentsRequestsQueue(), PaymentsConfig.paymentsInternalQueue()));
    private final MessageCatcher externalEvents = new MessageCatcher();

    public PaymentExternalEventForwarderTests() {
        messageBus.use(message -> {
            if (message instanceof PaymentExternalEvent) externalEvents.catchMessage(message);
        });
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
                TestTracing.untraced(),
                messageBus,
                paymentGateway,
                Payment.Validity.of(paymentTimeout),
                () -> now);
    }

    @Test
    public void authorizingForwardsPaymentAuthorizedWithTheReference() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        externalEvents.reset();

        messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore)));

        externalEvents.shouldReceiveMessages(new PaymentAuthorized(referenceId, paymentId, now));
    }

    @Test
    public void requestingACaptureForwardsNothing() {
        authorize();

        messageBus.publish(List.of(new CapturePayment(paymentId)));

        externalEvents.shouldNotReceiveAnyEvent();
    }

    @Test
    public void confirmedCaptureForwardsPaymentCapturedWithTheReference() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId)));

        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));

        externalEvents.shouldReceiveMessages(new PaymentCaptured(referenceId, paymentId, now));
    }

    @Test
    public void failedCaptureForwardsPaymentFailedWithTheReference() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId)));

        messageBus.publish(List.of(new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError)));

        externalEvents.shouldReceiveMessages(new PaymentFailed(referenceId, paymentId, now));
    }

    @Test
    public void timingOutForwardsPaymentFailed() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        externalEvents.reset();

        messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt)));

        externalEvents.shouldReceiveMessages(new PaymentFailed(referenceId, paymentId, timedOutAt));
    }

    @Test
    public void expiringForwardsPaymentFailed() {
        authorize();

        messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, captureBefore)));

        externalEvents.shouldReceiveMessages(new PaymentFailed(referenceId, paymentId, captureBefore));
    }

    @Test
    public void requestingAnAuthorizationForwardsNothing() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

        externalEvents.shouldNotReceiveAnyEvent();
    }

    @Test
    public void voidingForwardsNothingBecauseNobodyWaitsOnIt() {
        authorize();

        messageBus.publish(List.of(new CancelPayment(paymentId)));

        externalEvents.shouldNotReceiveAnyEvent();
    }

    private void authorize() {
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        externalEvents.reset();
    }

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
        bus.use(published::catchMessage);
        var forwarder = new PaymentExternalEventForwarder(summaries, bus);
        var streamId = PaymentFacade.mapToStreamId(paymentId);
        var timedOut = new EventEnvelope<>(new PaymentEvent.PaymentTimedOut(paymentId, now), Metadata.at(streamId, 1));

        assertThatThrownBy(() -> forwarder.onTimedOut(timedOut)).isInstanceOf(IllegalStateException.class);
        published.shouldNotReceiveAnyEvent();

        summaries.handle(new EventEnvelope<>(
                new PaymentEvent.PaymentAuthorizationRequested(
                        paymentId, referenceId, amount, now, now.plus(paymentTimeout)),
                Metadata.at(streamId, 0)));
        forwarder.onTimedOut(timedOut);

        published.shouldReceiveMessages(new PaymentFailed(referenceId, paymentId, now));
    }
}
