package io.eventdriven.distributedprocesses.ecommerce.payments.capture;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.CaptureFailureReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class CaptureGatewayClientTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void gatewayIsAskedToCaptureTheAuthorizedAmount() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);

    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new CapturePayment(paymentId)
    ));

    assertThat(gateway.calls).containsExactly(
      "authorize:%s:%s".formatted(paymentId, amount),
      "capture:%s:%s".formatted(paymentId, amount)
    );
  }

  @Test
  public void captureConfirmedByTheGatewayCapturesThePayment() {
    configure(new RecordingPaymentGateway(messageBus));
    authorize();

    messageBus.publish(List.of(new CapturePayment(paymentId)));

    published.shouldReceiveMessages(
      new PaymentCaptureRequested(paymentId, amount, now),
      new PaymentCaptured(paymentId, amount, now)
    );
  }

  @Test
  public void unreachableGatewayFailsTheCaptureInsteadOfThrowing() {
    configure(new RefusingAfterAuthorizationPaymentGateway(messageBus));
    authorize();

    assertThatCode(() -> messageBus.publish(List.of(new CapturePayment(paymentId))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(
      new PaymentCaptureRequested(paymentId, amount, now),
      new PaymentCaptureFailed(paymentId, CaptureFailureReason.UnexpectedError, now)
    );
  }

  @Test
  public void silentGatewayLeavesTheCaptureRequested() {
    configure(new SilentPaymentGateway());
    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    ));
    published.reset();

    messageBus.publish(List.of(new CapturePayment(paymentId)));

    published.shouldReceiveMessages(new PaymentCaptureRequested(paymentId, amount, now));
  }

  private void authorize() {
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();
  }

  private void configure(PaymentGateway paymentGateway) {
    eventStore.use(published::catchMessage);

    PaymentsConfig.configure(
      eventStore,
      messageBus,
      paymentGateway,
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }
}
