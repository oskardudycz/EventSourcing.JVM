package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoAuthorizingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoRejectingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.ThrowingPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AuthorizationGatewayClientTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plus(authorizationValidity);
  private final PaymentAuthorizationRequested requested =
    new PaymentAuthorizationRequested(paymentId, referenceId, amount, now);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void autoAuthorizingGatewayGrantsTheHold() {
    configure(new AutoAuthorizingPaymentGateway(messageBus));

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    published.shouldReceiveMessages(requested, new PaymentAuthorized(paymentId, now, expiresAt));
  }

  @Test
  public void autoRejectingGatewayDeclinesTheRequestedAuthorization() {
    configure(new AutoRejectingPaymentGateway(messageBus));

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    published.shouldReceiveMessages(
      requested,
      new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now)
    );
  }

  @Test
  public void unreachableGatewayDeclinesThePaymentInsteadOfThrowing() {
    configure(new ThrowingPaymentGateway());

    assertThatCode(() -> messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(
      requested,
      new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now)
    );
  }

  @Test
  public void silentGatewayLeavesTheAuthorizationPending() {
    configure(new SilentPaymentGateway());

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    published.shouldReceiveMessages(requested);
  }

  @Test
  public void gatewayIsAuthorizedWithThePaymentIdAndAmount() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    assertThat(gateway.calls).containsExactly("authorize:%s:%s".formatted(paymentId, amount));
  }

  @Test
  public void gatewayThatRefusesTheVoidLeavesTheRecordedOutcomeAloneInsteadOfThrowing() {
    configure(new RefusingAfterAuthorizationPaymentGateway(messageBus));

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    assertThatCode(() -> messageBus.publish(List.of(new VoidPayment(paymentId))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(new PaymentVoided(paymentId, now));
  }

  @Test
  public void timedOutPaymentCancelsTheRequestAtTheGateway() {
    var cancelled = new ArrayList<PaymentId>();
    configure(new SilentPaymentGateway() {
      @Override
      public void voidAuthorization(PaymentId paymentId) {
        cancelled.add(paymentId);
      }
    });

    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new TimeOutPayment(paymentId, now)
    ));

    assertThat(cancelled).containsExactly(paymentId);
  }

  @Test
  public void gatewayThatRefusesToCancelTheTimedOutRequestLeavesTheRecordedOutcomeAloneInsteadOfThrowing() {
    configure(new SilentPaymentGateway() {
      @Override
      public void voidAuthorization(PaymentId paymentId) {
        throw new RuntimeException("Payment provider is unreachable");
      }
    });
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    assertThatCode(() -> messageBus.publish(List.of(new TimeOutPayment(paymentId, now))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(new PaymentTimedOut(paymentId, now));
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
