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

public class PaymentTimeoutWorkerTests {
  private static final Duration timeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime requestedAt = OffsetDateTime.now();
  private final OffsetDateTime beforeThreshold = requestedAt.plusMinutes(1);
  private final OffsetDateTime afterThreshold = requestedAt.plusMinutes(10);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(requestedAt);
  private final ScheduledPaymentTimeoutWorker worker;

  public PaymentTimeoutWorkerTests() {
    eventStore.use(published::catchMessage);
    worker = PaymentsConfig.configure(
      eventStore,
      messageBus,
      new SilentPaymentGateway(),
      timeout,
      authorizationValidity,
      clock::get
    ).timeoutWorker();
  }

  @Test
  public void authorizationThatIsNeverAnsweredIsTimedOutOnceTheThresholdPasses() {
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    handleAllAt(afterThreshold);

    published.shouldReceiveMessages(new PaymentTimedOut(paymentId, afterThreshold));
  }

  @Test
  public void runningBeforeTheThresholdSendsNothing() {
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    handleAllAt(beforeThreshold);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void authorizationGrantedBeforeTheThresholdIsNeverTimedOut() {
    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    ));
    published.reset();

    handleAllAt(afterThreshold);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningTwiceTimesTheSamePaymentOutOnlyOnce() {
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    handleAllAt(afterThreshold);
    assertThatCode(() -> handleAllAt(afterThreshold.plusMinutes(1))).doesNotThrowAnyException();

    assertThat(published.published).containsOnlyOnce(new PaymentTimedOut(paymentId, afterThreshold));
    assertThat(published.published).hasSize(1);
  }

  private void handleAllAt(OffsetDateTime now) {
    clock.set(now);
    worker.handleAll();
  }
}
