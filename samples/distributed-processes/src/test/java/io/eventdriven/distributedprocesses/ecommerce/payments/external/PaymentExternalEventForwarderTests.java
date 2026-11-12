package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public PaymentExternalEventForwarderTests() {
    messageBus.use(message -> {
      if (message instanceof PaymentExternalEvent)
        externalEvents.catchMessage(message);
    });
    PaymentsConfig.configure(
      eventStore,
      messageBus,
      new SilentPaymentGateway(),
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }

  @Test
  public void authorizingForwardsPaymentAuthorizedWithTheReferenceAmountAndDeadline() {
    messageBus.publish(List.of(new AuthorizePayment(referenceId, amount)));
    externalEvents.reset();

    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));

    externalEvents.shouldReceiveMessages(
      new PaymentAuthorized(referenceId, paymentId, amount, now, expiresAt)
    );
  }

  @Test
  public void capturingForwardsPaymentCapturedWithTheReferenceAndAmount() {
    authorize();

    messageBus.publish(List.of(new CapturePayment(paymentId)));

    externalEvents.shouldReceiveMessages(
      new PaymentCaptured(referenceId, paymentId, amount, now)
    );
  }

  @Test
  public void decliningForwardsPaymentFailedAsDeclined() {
    messageBus.publish(List.of(new AuthorizePayment(referenceId, amount)));
    externalEvents.reset();

    messageBus.publish(List.of(new DeclinePayment(paymentId, DeclineReason.UnexpectedError)));

    externalEvents.shouldReceiveMessages(
      new PaymentFailed(referenceId, paymentId, amount, now, PaymentFailed.Reason.Declined)
    );
  }

  @Test
  public void timingOutForwardsPaymentFailedAsTimedOut() {
    messageBus.publish(List.of(new AuthorizePayment(referenceId, amount)));
    externalEvents.reset();

    messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt)));

    externalEvents.shouldReceiveMessages(
      new PaymentFailed(referenceId, paymentId, amount, timedOutAt, PaymentFailed.Reason.TimedOut)
    );
  }

  @Test
  public void expiringForwardsPaymentFailedAsAuthorizationExpired() {
    authorize();

    messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, expiresAt)));

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
    messageBus.publish(List.of(new AuthorizePayment(referenceId, amount)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void voidingForwardsNothingBecauseNobodyWaitsOnIt() {
    authorize();

    messageBus.publish(List.of(new VoidPayment(paymentId)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void refundingForwardsNothingBecauseNobodyWaitsOnIt() {
    authorize();
    messageBus.publish(List.of(new CapturePayment(paymentId)));
    externalEvents.reset();

    messageBus.publish(List.of(new RefundPayment(paymentId)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void authorize() {
    messageBus.publish(List.of(
      new AuthorizePayment(referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    ));
    externalEvents.reset();
  }

  @Test
  public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
    var summaries = new PaymentSummaries(new Database());
    var bus = new InMemoryMessageBus();
    var published = new MessageCatcher();
    bus.use(published::catchMessage);
    var forwarder = new PaymentExternalEventForwarder(summaries, bus);
    var streamId = PaymentFacade.mapToStreamId(paymentId);

    summaries.onAuthorizationRequested(new EventEnvelope<>(
      new PaymentEvent.PaymentAuthorizationRequested(paymentId, referenceId, amount),
      new EventMetadata(streamId, 0, 0)
    ));
    var authorized = new EventEnvelope<>(
      new PaymentEvent.PaymentAuthorized(paymentId, now, expiresAt),
      new EventMetadata(streamId, 1, 1)
    );

    assertThatThrownBy(() -> forwarder.onAuthorized(authorized)).isInstanceOf(IllegalStateException.class);
    published.shouldNotReceiveAnyEvent();

    summaries.onAuthorized(authorized);
    forwarder.onAuthorized(authorized);

    published.shouldReceiveMessages(new PaymentAuthorized(referenceId, paymentId, amount, now, expiresAt));
  }
}
