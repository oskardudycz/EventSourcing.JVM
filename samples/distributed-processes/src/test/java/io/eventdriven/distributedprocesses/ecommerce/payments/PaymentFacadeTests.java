package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
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

public class PaymentFacadeTests {
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
  private final MessageCatcher published = new MessageCatcher();

  public PaymentFacadeTests() {
    eventStore.use(published::catchMessage);
    PaymentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      new SilentPaymentGateway(),
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }

  @Test
  public void authorizePaymentDispatchesOnlyTheRequestAndLeavesItPending() {
    commandBus.send(new AuthorizePayment(referenceId, amount));

    var requested = new PaymentAuthorizationRequested(paymentId, referenceId, amount);

    published.shouldReceiveMessages(requested);
    assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(requested));

    // Only a pending authorisation can still be confirmed
    commandBus.send(new ConfirmPaymentAuthorization(paymentId));

    assertThat(storedEvents()).hasSize(2);
  }

  @Test
  public void confirmPaymentAuthorizationDispatchesPaymentAuthorizedWithTheDeadlineItGrants() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    commandBus.send(new ConfirmPaymentAuthorization(paymentId));

    published.shouldReceiveMessages(new PaymentAuthorized(paymentId, now, expiresAt));
  }

  @Test
  public void capturePaymentOnAnAuthorizedPaymentDispatchesPaymentCaptured() {
    authorize();

    commandBus.send(new CapturePayment(paymentId));

    published.shouldReceiveMessages(new PaymentCaptured(paymentId, amount, now));
  }

  @Test
  public void voidPaymentOnAnAuthorizedPaymentDispatchesPaymentVoided() {
    authorize();

    commandBus.send(new VoidPayment(paymentId));

    published.shouldReceiveMessages(new PaymentVoided(paymentId, now));
  }

  @Test
  public void voidPaymentOnACapturedPaymentStoresNothingAndDoesNotThrow() {
    authorize();
    commandBus.send(new CapturePayment(paymentId));
    published.reset();

    assertThatCode(() -> commandBus.send(new VoidPayment(paymentId)))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).hasSize(3);
  }

  @Test
  public void refundPaymentOnACapturedPaymentDispatchesPaymentRefunded() {
    authorize();
    commandBus.send(new CapturePayment(paymentId));
    published.reset();

    commandBus.send(new RefundPayment(paymentId));

    published.shouldReceiveMessages(new PaymentRefunded(paymentId, now));
    assertThat(storedEvents()).hasSize(4);
  }

  @Test
  public void refundPaymentOnAnAuthorizedPaymentStoresNothingAndDoesNotThrow() {
    authorize();

    assertThatCode(() -> commandBus.send(new RefundPayment(paymentId)))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).hasSize(2);
  }

  @Test
  public void declinePaymentDispatchesPaymentDeclinedWithTheReason() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    commandBus.send(new DeclinePayment(paymentId, DeclineReason.UnexpectedError));

    published.shouldReceiveMessages(
      new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now)
    );
  }

  @Test
  public void timeOutPaymentDispatchesPaymentTimedOut() {
    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    commandBus.send(new TimeOutPayment(paymentId, timedOutAt));

    published.shouldReceiveMessages(new PaymentTimedOut(paymentId, timedOutAt));
  }

  @Test
  public void expirePaymentAuthorizationDispatchesPaymentAuthorizationExpired() {
    authorize();

    commandBus.send(new ExpirePaymentAuthorization(paymentId, expiresAt));

    published.shouldReceiveMessages(new PaymentAuthorizationExpired(paymentId, expiresAt));
  }

  @Test
  public void confirmingAnAlreadyAuthorizedPaymentStoresNothingAndDoesNotThrow() {
    authorize();

    assertThatCode(() -> commandBus.send(new ConfirmPaymentAuthorization(paymentId)))
      .doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).hasSize(2);
  }

  @Test
  public void authorizePaymentDerivesThePaymentIdFromTheReferenceSoASecondRequestChangesNothing() {
    commandBus.send(
      new AuthorizePayment(referenceId, amount),
      new AuthorizePayment(referenceId, amount)
    );

    assertThat(storedEvents()).usingRecursiveComparison()
      .isEqualTo(List.of(new PaymentAuthorizationRequested(paymentId, referenceId, amount)));
  }

  @Test
  public void mapsIdToItsOwnStream() {
    assertThat(PaymentFacade.mapToStreamId(paymentId))
      .isEqualTo("Payment-%s".formatted(paymentId.tail()));
  }

  private void authorize() {
    commandBus.send(
      new AuthorizePayment(referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId)
    );
    published.reset();
  }

  private List<Object> storedEvents() {
    return switch (eventStore.read(PaymentFacade.mapToStreamId(paymentId))) {
      case EventStore.ReadResult.Success success -> List.of(success.events());
      default -> List.of();
    };
  }
}
