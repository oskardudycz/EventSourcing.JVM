package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
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
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final double amount = 62.5;
    private final OffsetDateTime authorizedAt = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = authorizedAt.plus(authorizationValidity);
    private final OffsetDateTime beforeExpiry = captureBefore.minusDays(1);
    private final OffsetDateTime afterExpiry = captureBefore.plusDays(1);

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced());
    private final MessageCatcher published = new MessageCatcher();

    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(authorizedAt);
    private final ScheduledAuthorizationExpiryWorker worker;

    public AuthorizationExpiryWorkerTests() {
        eventStore.use(event -> {
            if (event.toString().contains(paymentId.value())) published.catchMessage(event);
        });
        worker = PaymentsConfig.configure(
                        eventStore,
                        messageBus,
                        new SilentPaymentGateway(),
                        PaymentValidity.of(paymentTimeout),
                        H2.bean(PaymentSummaryRepository.class),
                        H2.bean(PendingPaymentRepository.class),
                        H2.bean(AuthorizedPaymentRepository.class),
                        clock::get)
                .authorizationExpiryWorker();
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
