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
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class AuthorizationExpiryWorkerTests {
    private static final Duration paymentTimeout = Duration.ofMinutes(5);
    private static final Duration authorizationValidity = Duration.ofDays(7);

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime authorizedAt = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = authorizedAt.plus(authorizationValidity);
    private final OffsetDateTime beforeExpiry = captureBefore.minusDays(1);
    private final OffsetDateTime afterExpiry = captureBefore.plusDays(1);

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

    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(authorizedAt);
    private final ScheduledAuthorizationExpiryWorker worker;

    public AuthorizationExpiryWorkerTests() {
        eventStore.use(event -> {
            if (event.toString().contains(paymentId.value())) published.catchMessage(event);
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
                clock::get);
        worker = paymentsConfig.authorizationExpiryWorker(authorizedPayments, messageBus, clock::get);
    }

    @Test
    public void holdThatIsNeverCapturedExpiresOnceItsDeadlinePasses() {
        authorize();

        handleAllAt(afterExpiry);

        published.shouldReceiveMessages(new PaymentAuthorizationExpired(paymentId, afterExpiry));
    }

    @Test
    public void runningBeforeTheDeadlineSendsNothing() {
        authorize();

        handleAllAt(beforeExpiry);

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void paymentWithARequestedCaptureNeverExpires() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId)));
        published.reset();

        handleAllAt(afterExpiry);

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void voidedPaymentNeverExpires() {
        authorize();
        messageBus.publish(List.of(new CancelPayment(paymentId)));
        published.reset();

        handleAllAt(afterExpiry);

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void runningTwiceExpiresTheSameHoldOnlyOnce() {
        authorize();

        handleAllAt(afterExpiry);
        assertThatCode(() -> handleAllAt(afterExpiry.plusDays(1))).doesNotThrowAnyException();

        assertThat(published.published).containsOnlyOnce(new PaymentAuthorizationExpired(paymentId, afterExpiry));
        assertThat(published.published).hasSize(1);
    }

    private void authorize() {
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        published.reset();
    }

    private void handleAllAt(OffsetDateTime now) {
        clock.set(now);
        worker.handleAll();
    }
}
