package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

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
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  private final AuthorizationExpiryWorker worker;

  public AuthorizationExpiryWorkerTests() {
    eventStore.use(published::catchMessage);
    worker = PaymentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      new SilentPaymentGateway(),
      paymentTimeout,
      authorizationValidity,
      () -> authorizedAt
    ).authorizationExpiryWorker();
  }

  @Test
  public void holdThatIsNeverCapturedExpiresOnceItsDeadlinePasses() {
    authorize();

    worker.run(afterExpiry);

    published.shouldReceiveMessages(new PaymentAuthorizationExpired(paymentId, afterExpiry));
  }

  @Test
  public void runningBeforeTheDeadlineSendsNothing() {
    authorize();

    worker.run(beforeExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void capturedPaymentNeverExpires() {
    authorize();
    commandBus.send(new CapturePayment(paymentId));
    published.reset();

    worker.run(afterExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void voidedPaymentNeverExpires() {
    authorize();
    commandBus.send(new VoidPayment(paymentId));
    published.reset();

    worker.run(afterExpiry);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningTwiceExpiresTheSameHoldOnlyOnce() {
    authorize();

    worker.run(afterExpiry);
    assertThatCode(() -> worker.run(afterExpiry.plusDays(1))).doesNotThrowAnyException();

    assertThat(published.published)
      .containsOnlyOnce(new PaymentAuthorizationExpired(paymentId, afterExpiry));
    assertThat(published.published).hasSize(1);
  }

  private void authorize() {
    commandBus.send(
      new AuthorizePayment(referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    );
    published.reset();
  }
}
