package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;

public class PaymentExternalEventForwarderTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plus(authorizationValidity);
  private final OffsetDateTime timedOutAt = now.plusMinutes(5);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public PaymentExternalEventForwarderTests() {
    integrationEventBus.use(externalEvents::catchMessage);
    PaymentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      integrationEventBus,
      new SilentPaymentGateway(),
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }

  @Test
  public void authorizingForwardsPaymentAuthorizedWithTheReferenceAmountAndDeadline() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    externalEvents.reset();

    commandBus.send(new ConfirmPaymentAuthorization(paymentId));

    externalEvents.shouldReceiveMessages(
      new PaymentAuthorized(referenceId, paymentId, amount, now, expiresAt)
    );
  }

  @Test
  public void capturingForwardsPaymentCapturedWithTheReferenceAndAmount() {
    authorize();

    commandBus.send(new CapturePayment(paymentId));

    externalEvents.shouldReceiveMessages(
      new PaymentCaptured(referenceId, paymentId, amount, now)
    );
  }

  @Test
  public void decliningForwardsPaymentFailedAsDeclined() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    externalEvents.reset();

    commandBus.send(new DeclinePayment(paymentId, DeclineReason.UnexpectedError));

    externalEvents.shouldReceiveMessages(
      new PaymentFailed(referenceId, paymentId, amount, now, PaymentFailed.Reason.Declined)
    );
  }

  @Test
  public void timingOutForwardsPaymentFailedAsTimedOut() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    externalEvents.reset();

    commandBus.send(new TimeOutPayment(paymentId, timedOutAt));

    externalEvents.shouldReceiveMessages(
      new PaymentFailed(referenceId, paymentId, amount, timedOutAt, PaymentFailed.Reason.TimedOut)
    );
  }

  @Test
  public void expiringForwardsPaymentFailedAsAuthorizationExpired() {
    authorize();

    commandBus.send(new ExpirePaymentAuthorization(paymentId, expiresAt));

    externalEvents.shouldReceiveMessages(new PaymentFailed(
      referenceId,
      paymentId,
      amount,
      expiresAt,
      PaymentFailed.Reason.AuthorizationExpired
    ));
  }

  @Test
  public void requestingAnAuthorizationForwardsNothing() {
    commandBus.send(new AuthorizePayment(referenceId, amount));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void voidingForwardsNothingBecauseNobodyWaitsOnIt() {
    authorize();

    commandBus.send(new VoidPayment(paymentId));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void refundingForwardsNothingBecauseNobodyWaitsOnIt() {
    authorize();
    commandBus.send(new CapturePayment(paymentId));
    externalEvents.reset();

    commandBus.send(new RefundPayment(paymentId));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void authorize() {
    commandBus.send(
      new AuthorizePayment(referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    );
    externalEvents.reset();
  }
}
