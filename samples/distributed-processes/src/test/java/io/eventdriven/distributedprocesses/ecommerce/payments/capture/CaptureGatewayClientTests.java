package io.eventdriven.distributedprocesses.ecommerce.payments.capture;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.CaptureFailureReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CaptureGatewayClientTests {
    private static final Duration paymentTimeout = Duration.ofMinutes(5);
    private static final Duration authorizationValidity = Duration.ofDays(7);

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = now.plus(authorizationValidity);

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(PaymentsConfig.paymentsRoutes()),
            List.of(
                    PaymentsConfig.paymentsRequestsChannel(),
                    PaymentsConfig.paymentsResponsesChannel(),
                    PaymentsConfig.paymentsInternalChannel()),
            List.of(PaymentsConfig.paymentsRequestsQueue(), PaymentsConfig.paymentsInternalQueue()));
    private final MessageCatcher published = new MessageCatcher();

    @Test
    public void gatewayIsAskedToCaptureTheAuthorizedAmount() {
        var gateway = new RecordingPaymentGateway(messageBus);
        configure(gateway);

        authorize();

        messageBus.publish(List.of(new CapturePayment(paymentId)));

        assertThat(gateway.calls).containsExactly("capture:%s:%s".formatted(paymentId, amount));
    }

    @Test
    public void captureConfirmedByTheGatewayCapturesThePayment() {
        configure(new RecordingPaymentGateway(messageBus));
        authorize();

        messageBus.publish(List.of(new CapturePayment(paymentId)));

        published.shouldReceiveMessages(
                new PaymentCaptureRequested(paymentId, amount, now), new PaymentCaptured(paymentId, amount, now));
    }

    @Test
    public void unreachableGatewayFailsTheCaptureInsteadOfThrowing() {
        configure(new RefusingAfterAuthorizationPaymentGateway());
        authorize();

        assertThatCode(() -> messageBus.publish(List.of(new CapturePayment(paymentId))))
                .doesNotThrowAnyException();

        published.shouldReceiveMessages(
                new PaymentCaptureRequested(paymentId, amount, now),
                new PaymentCaptureFailed(paymentId, CaptureFailureReason.UnexpectedError, now));
    }

    @Test
    public void silentGatewayLeavesTheCaptureRequested() {
        configure(new SilentPaymentGateway());
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        published.reset();

        messageBus.publish(List.of(new CapturePayment(paymentId)));

        published.shouldReceiveMessages(new PaymentCaptureRequested(paymentId, amount, now));
    }

    private void authorize() {
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        published.reset();
    }

    private void configure(PaymentGateway paymentGateway) {
        eventStore.use(published::catchMessage);

        var paymentsConfig = new PaymentsConfig();
        var paymentSummaries = paymentsConfig.paymentSummaries(eventStore, H2.bean(PaymentSummaryRepository.class));
        var pendingPayments = paymentsConfig.pendingPayments(eventStore, H2.bean(PendingPaymentRepository.class));
        var authorizedPayments =
                paymentsConfig.authorizedPayments(eventStore, H2.bean(AuthorizedPaymentRepository.class));
        paymentsConfig.paymentExternalEventForwarder(eventStore, paymentSummaries, messageBus);
        paymentsConfig.captureGatewayClient(eventStore, paymentGateway, messageBus);
        paymentsConfig.authorizationGatewayClient(eventStore, paymentGateway);
        paymentsConfig.paymentFacade(
                eventStore, messageBus, paymentGateway, PaymentValidity.of(paymentTimeout), () -> now);
    }
}
