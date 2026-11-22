package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class PaymentFacadeTests {
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
    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(now);
    private final SilentPaymentGateway gateway = new SilentPaymentGateway();
    private final URI orderStatusPage = URI.create("http://localhost:8080/customers/1/orders/2");
    private final PaymentFacade facade;

    public PaymentFacadeTests() {
        eventStore.use(published::catchMessage);
        var paymentsConfig = new PaymentsConfig();
        var paymentSummaries = paymentsConfig.paymentSummaries(eventStore, H2.bean(PaymentSummaryRepository.class));
        var pendingPayments = paymentsConfig.pendingPayments(eventStore, H2.bean(PendingPaymentRepository.class));
        var authorizedPayments =
                paymentsConfig.authorizedPayments(eventStore, H2.bean(AuthorizedPaymentRepository.class));
        paymentsConfig.paymentExternalEventForwarder(eventStore, paymentSummaries, messageBus);
        paymentsConfig.captureGatewayClient(eventStore, gateway, messageBus);
        paymentsConfig.authorizationGatewayClient(eventStore, gateway);
        facade = paymentsConfig.paymentFacade(
                eventStore,
                TestTracing.untraced(),
                messageBus,
                gateway,
                Payment.Validity.of(paymentTimeout),
                clock::get);
    }

    @Test
    public void createCheckoutSessionOpensASessionAtTheGatewayAndRecordsIt() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        var session = facade.createCheckoutSession(createCheckoutSession());

        var expected = new PaymentGateway.CheckoutSession(checkoutPageOf(paymentId), now.plusMinutes(30));
        assertThat(session).isEqualTo(expected);
        published.shouldReceiveMessages(new CheckoutSessionCreated(paymentId, expected.url(), expected.expiresAt()));
    }

    @Test
    public void createCheckoutSessionWhileOneIsOpenReturnsItWithoutAskingTheGatewayAgain() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        var first = facade.createCheckoutSession(createCheckoutSession());
        published.reset();
        clock.set(now.plusMinutes(29));

        var second = facade.createCheckoutSession(createCheckoutSession());

        assertThat(second).isEqualTo(first);
        assertThat(gateway.checkoutSessionsCreated).containsExactly(paymentId);
        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void createCheckoutSessionAfterTheSessionExpiredOpensANewOneForTheSamePayment() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        facade.createCheckoutSession(createCheckoutSession());
        published.reset();
        clock.set(now.plusMinutes(30));

        var session = facade.createCheckoutSession(createCheckoutSession());

        assertThat(session.expiresAt()).isEqualTo(now.plusMinutes(60));
        published.shouldReceiveMessages(new CheckoutSessionCreated(paymentId, session.url(), session.expiresAt()));
    }

    @Test
    public void createCheckoutSessionForAnAuthorizedPaymentIsRefusedWithoutAskingTheGateway() {
        authorize();

        assertThatThrownBy(() -> facade.createCheckoutSession(createCheckoutSession()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(gateway.checkoutSessionsCreated).isEmpty();
        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void createCheckoutSessionForAPaymentOfAnotherOrderIsNotFound() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        var anotherOrder = Urns.of("ecommerce", "order", UUID.randomUUID());

        assertThatThrownBy(() -> facade.createCheckoutSession(new PaymentFacade.CreateCheckoutSession(
                        paymentId, anotherOrder, orderStatusPage, orderStatusPage)))
                .isInstanceOf(NoSuchElementException.class);
        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void createCheckoutSessionForAnUnknownPaymentIsNotFound() {
        assertThatThrownBy(() -> facade.createCheckoutSession(createCheckoutSession()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void createCheckoutSessionThatTheGatewayFailsToOpenRecordsNothing() {
        var store = new InMemoryEventStore(TestTracing.untraced(), ECommerceMessages.serializer());
        var bus = new InMemoryMessageBus(
                TestTracing.untraced(),
                List.of(PaymentsConfig.paymentsRoutes()),
                List.of(
                        PaymentsConfig.paymentsRequestsChannel(),
                        PaymentsConfig.paymentsResponsesChannel(),
                        PaymentsConfig.paymentsInternalChannel()),
                List.of(PaymentsConfig.paymentsRequestsQueue(), PaymentsConfig.paymentsInternalQueue()));
        var paymentGateway = new ThrowingPaymentGateway();
        var paymentsConfig = new PaymentsConfig();
        var paymentSummaries = paymentsConfig.paymentSummaries(store, H2.bean(PaymentSummaryRepository.class));
        var pendingPayments = paymentsConfig.pendingPayments(store, H2.bean(PendingPaymentRepository.class));
        var authorizedPayments = paymentsConfig.authorizedPayments(store, H2.bean(AuthorizedPaymentRepository.class));
        paymentsConfig.paymentExternalEventForwarder(store, paymentSummaries, bus);
        paymentsConfig.captureGatewayClient(store, paymentGateway, bus);
        paymentsConfig.authorizationGatewayClient(store, paymentGateway);
        var throwingFacade = paymentsConfig.paymentFacade(
                store, TestTracing.untraced(), bus, paymentGateway, Payment.Validity.of(paymentTimeout), clock::get);
        bus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

        assertThatThrownBy(() -> throwingFacade.createCheckoutSession(createCheckoutSession()))
                .isInstanceOf(PaymentGateway.GatewayException.class)
                .hasRootCauseMessage("Payment provider is unreachable");

        assertThat(store.read(PaymentFacade.mapToStreamId(paymentId)))
                .isInstanceOfSatisfying(
                        EventStore.ReadResult.Success.class,
                        read -> assertThat(read.events()).hasSize(1));
    }

    @Test
    public void authorizePaymentDispatchesOnlyTheRequestAndLeavesItPending() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

        var requested = new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timedOutAt);

        published.shouldReceiveMessages(requested);
        assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(requested));

        // Only a pending authorization can still be confirmed
        messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore)));

        assertThat(storedEvents()).hasSize(2);
    }

    @Test
    public void confirmPaymentAuthorizationDispatchesPaymentAuthorizedWithTheDeadlineItGrants() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore)));

        published.shouldReceiveMessages(new PaymentAuthorized(paymentId, now, captureBefore));
    }

    @Test
    public void capturePaymentOnAnAuthorizedPaymentDispatchesOnlyTheRequest() {
        authorize();

        messageBus.publish(List.of(new CapturePayment(paymentId)));

        published.shouldReceiveMessages(new PaymentCaptureRequested(paymentId, amount, now));
    }

    @Test
    public void confirmPaymentCaptureDispatchesPaymentCapturedForTheAuthorizedAmount() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId)));
        published.reset();

        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));

        published.shouldReceiveMessages(new PaymentCaptured(paymentId, amount, now));
    }

    @Test
    public void failPaymentCaptureDispatchesPaymentCaptureFailedWithTheReason() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId)));
        published.reset();

        messageBus.publish(List.of(new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError)));

        published.shouldReceiveMessages(
                new PaymentCaptureFailed(paymentId, Payment.CaptureFailureReason.UnexpectedError, now));
    }

    @Test
    public void cancelPaymentOnAnAuthorizedPaymentDispatchesPaymentCanceled() {
        authorize();

        messageBus.publish(List.of(new CancelPayment(paymentId)));

        published.shouldReceiveMessages(new PaymentCanceled(paymentId, now));
    }

    @Test
    public void cancelPaymentOnACapturedPaymentStoresNothingAndDoesNotThrow() {
        authorize();
        messageBus.publish(List.of(new CapturePayment(paymentId), new ConfirmPaymentCapture(paymentId)));
        published.reset();

        assertThatCode(() -> messageBus.publish(List.of(new CancelPayment(paymentId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(4);
    }

    @Test
    public void timeOutPaymentDispatchesPaymentTimedOut() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt)));

        published.shouldReceiveMessages(new PaymentTimedOut(paymentId, timedOutAt));
    }

    @Test
    public void timeOutPaymentBeforeItsDeadlineStoresNothingAndDoesNotThrow() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        assertThatCode(() -> messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt.minusMinutes(1)))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(1);
    }

    @Test
    public void expirePaymentAuthorizationDispatchesPaymentAuthorizationExpired() {
        authorize();

        messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, captureBefore)));

        published.shouldReceiveMessages(new PaymentAuthorizationExpired(paymentId, captureBefore));
    }

    @Test
    public void expirePaymentAuthorizationBeforeItsDeadlineStoresNothingAndDoesNotThrow() {
        authorize();

        assertThatCode(() -> messageBus.publish(
                        List.of(new ExpirePaymentAuthorization(paymentId, captureBefore.minusMinutes(1)))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(2);
    }

    @Test
    public void capturePaymentAfterTheAuthorizationExpiredStoresNothingAndDoesNotThrow() {
        authorize();
        messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, captureBefore)));
        published.reset();

        assertThatCode(() -> messageBus.publish(List.of(new CapturePayment(paymentId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(3);
    }

    @Test
    public void confirmingAnAlreadyAuthorizedPaymentStoresNothingAndDoesNotThrow() {
        authorize();

        assertThatCode(() -> messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(2);
    }

    @Test
    public void authorizePaymentDerivesThePaymentIdFromTheReferenceSoASecondRequestChangesNothing() {
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new AuthorizePayment(paymentId, referenceId, amount)));

        assertThat(storedEvents())
                .usingRecursiveComparison()
                .isEqualTo(List.of(new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timedOutAt)));
    }

    @Test
    public void mapsIdToItsOwnStream() {
        assertThat(PaymentFacade.mapToStreamId(paymentId)).isEqualTo("Payment-%s".formatted(paymentId.tail()));
    }

    private PaymentFacade.CreateCheckoutSession createCheckoutSession() {
        return new PaymentFacade.CreateCheckoutSession(paymentId, referenceId, orderStatusPage, orderStatusPage);
    }

    private URI checkoutPageOf(Payment.Id paymentId) {
        return URI.create("https://checkout.example/c/pay/" + paymentId.tail());
    }

    private void authorize() {
        messageBus.publish(List.of(
                new AuthorizePayment(paymentId, referenceId, amount),
                new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        published.reset();
    }

    private List<Object> storedEvents() {
        return switch (eventStore.read(PaymentFacade.mapToStreamId(paymentId))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(MessageEnvelope::message).toList();
            default -> List.of();
        };
    }
}
