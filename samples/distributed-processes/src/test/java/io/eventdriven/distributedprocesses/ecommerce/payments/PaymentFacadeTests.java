package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
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
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = now.plus(authorizationValidity);
    private final OffsetDateTime timedOutAt = now.plus(paymentTimeout);

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced());
    private final MessageCatcher published = new MessageCatcher();
    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(now);
    private final SilentPaymentGateway gateway = new SilentPaymentGateway();
    private final URI orderStatusPage = URI.create("http://localhost:8080/customers/1/orders/2");
    private final PaymentFacade facade;

    public PaymentFacadeTests() {
        eventStore.use(published::catchMessage);
        facade = PaymentsConfig.configure(
                        eventStore,
                        messageBus,
                        gateway,
                        PaymentValidity.of(paymentTimeout),
                        H2.bean(PaymentSummaryRepository.class),
                        H2.bean(PendingPaymentRepository.class),
                        H2.bean(AuthorizedPaymentRepository.class),
                        clock::get)
                .facade();
    }

    @Test
    public void createCheckoutSessionOpensASessionAtTheGatewayAndRecordsIt() {
        messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
        published.reset();

        var session = facade.createCheckoutSession(createCheckoutSession());

        var expected = new CheckoutSession(checkoutPageOf(paymentId), now.plusMinutes(30));
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

        assertThatThrownBy(() -> facade.createCheckoutSession(
                        new CreateCheckoutSession(paymentId, anotherOrder, orderStatusPage, orderStatusPage)))
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
        var store = new InMemoryEventStore(TestTracing.untraced());
        var bus = new InMemoryMessageBus(TestTracing.untraced());
        var throwingFacade = PaymentsConfig.configure(
                        store,
                        bus,
                        new ThrowingPaymentGateway(),
                        PaymentValidity.of(paymentTimeout),
                        H2.bean(PaymentSummaryRepository.class),
                        H2.bean(PendingPaymentRepository.class),
                        H2.bean(AuthorizedPaymentRepository.class),
                        clock::get)
                .facade();
        bus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

        assertThatThrownBy(() -> throwingFacade.createCheckoutSession(createCheckoutSession()))
                .isInstanceOf(PaymentGatewayException.class)
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

        // Only a pending authorisation can still be confirmed
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

        messageBus.publish(List.of(new FailPaymentCapture(paymentId, CaptureFailureReason.UnexpectedError)));

        published.shouldReceiveMessages(new PaymentCaptureFailed(paymentId, CaptureFailureReason.UnexpectedError, now));
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

    private CreateCheckoutSession createCheckoutSession() {
        return new CreateCheckoutSession(paymentId, referenceId, orderStatusPage, orderStatusPage);
    }

    private URI checkoutPageOf(PaymentId paymentId) {
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
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }
}
