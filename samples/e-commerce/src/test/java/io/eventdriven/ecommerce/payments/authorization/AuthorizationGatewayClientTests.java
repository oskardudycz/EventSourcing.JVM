package io.eventdriven.ecommerce.payments.authorization;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class AuthorizationGatewayClientTests {
    private static final Duration paymentTimeout = Duration.ofMinutes(5);
    private static final Duration authorizationValidity = Duration.ofDays(7);

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = now.plus(authorizationValidity);
    private final OffsetDateTime timedOutAt = now.plus(paymentTimeout);

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
    private final MessageCatcher published = new MessageCatcher();

    @Test
    public void requestingAnAuthorizationAsksTheGatewayForNothingUntilTheCustomerPays() {
        var gateway = new RecordingPaymentGateway(messageBus);
        configure(gateway);

        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

        published.shouldReceiveMessages(
                new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timedOutAt));
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    public void canceledPaymentIsCanceledAtTheGateway() {
        var gateway = new RecordingPaymentGateway(messageBus);
        configure(gateway);
        authorize();

        messageBus.publish(List.of(new CancelPayment(paymentId)));

        assertThat(gateway.calls).containsExactly("cancel:%s".formatted(paymentId));
    }

    @Test
    public void gatewayThatRefusesToCancelLeavesTheRecordedOutcomeAloneInsteadOfThrowing() {
        configure(new RefusingAfterAuthorizationPaymentGateway());
        authorize();

        assertThatCode(() -> messageBus.publish(List.of(new CancelPayment(paymentId))))
                .doesNotThrowAnyException();

        published.shouldReceiveMessages(new PaymentCanceled(paymentId, now));
    }

    @Test
    public void timedOutPaymentExpiresItsCheckoutSessionAtTheGateway() {
        var gateway = new RecordingPaymentGateway(messageBus);
        configure(gateway);

        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount), new TimeOutPayment(paymentId, timedOutAt)));

        assertThat(gateway.calls).containsExactly("expire-checkout-session:%s".formatted(paymentId));
    }

    @Test
    public void gatewayThatRefusesToExpireTheSessionLeavesTheTimedOutPaymentAloneInsteadOfThrowing() {
        configure(new RefusingAfterAuthorizationPaymentGateway());
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        assertThatCode(() -> messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt))))
                .doesNotThrowAnyException();

        published.shouldReceiveMessages(new PaymentTimedOut(paymentId, timedOutAt));
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
                eventStore,
                TestTracing.untraced(),
                messageBus,
                paymentGateway,
                Payment.Validity.of(paymentTimeout),
                () -> now);
    }
}
