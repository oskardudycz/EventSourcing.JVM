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
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  private final PaymentTimeoutWorker worker;

  public PaymentTimeoutWorkerTests() {
    eventStore.use(published::catchMessage);
    worker = PaymentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      new SilentPaymentGateway(),
      timeout,
      authorizationValidity,
      () -> requestedAt
    ).timeoutWorker();
  }

  @Test
  public void authorizationThatIsNeverAnsweredIsTimedOutOnceTheThresholdPasses() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    worker.run(afterThreshold);

    published.shouldReceiveMessages(new PaymentTimedOut(paymentId, afterThreshold));
  }

  @Test
  public void runningBeforeTheThresholdSendsNothing() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    worker.run(beforeThreshold);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void authorizationGrantedBeforeTheThresholdIsNeverTimedOut() {
    commandBus.send(
      new AuthorizePayment(referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    );
    published.reset();

    worker.run(afterThreshold);

    published.shouldNotReceiveAnyEvent();
  }

  @Test
  public void runningTwiceTimesTheSamePaymentOutOnlyOnce() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    worker.run(afterThreshold);
    assertThatCode(() -> worker.run(afterThreshold.plusMinutes(1))).doesNotThrowAnyException();

    assertThat(published.published).containsOnlyOnce(new PaymentTimedOut(paymentId, afterThreshold));
    assertThat(published.published).hasSize(1);
  }
}
