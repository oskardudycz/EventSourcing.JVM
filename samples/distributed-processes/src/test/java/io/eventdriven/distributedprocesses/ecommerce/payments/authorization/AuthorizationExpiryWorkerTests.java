package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AuthorizationExpiryWorkerTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime authorizedAt = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = authorizedAt.plus(authorizationValidity);
  private final OffsetDateTime beforeExpiry = expiresAt.minusDays(1);
  private final OffsetDateTime afterExpiry = expiresAt.plusDays(1);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(authorizedAt);
  private final ScheduledAuthorizationExpiryWorker worker;

  public AuthorizationExpiryWorkerTests() {
    eventStore.use(published::catchMessage);
    worker = PaymentsConfig.configure(
      eventStore,
      messageBus,
      new SilentPaymentGateway(),
      paymentTimeout,
      authorizationValidity,
      clock::get
    ).authorizationExpiryWorker();
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
    messageBus.publish(List.of(new VoidPayment(paymentId)));
    published.reset();

    handleAllAt(afterExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningTwiceExpiresTheSameHoldOnlyOnce() {
    authorize();

    handleAllAt(afterExpiry);
    assertThatCode(() -> handleAllAt(afterExpiry.plusDays(1))).doesNotThrowAnyException();

    assertThat(published.published)
      .containsOnlyOnce(new PaymentAuthorizationExpired(paymentId, afterExpiry));
    assertThat(published.published).hasSize(1);
  }

  private void authorize() {
    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    ));
    published.reset();
  }

  private void handleAllAt(OffsetDateTime now) {
    clock.set(now);
    worker.handleAll();
  }
}
