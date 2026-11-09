package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoAuthorizingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.AutoRejectingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.ThrowingPaymentGateway;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
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
    new PaymentAuthorizationRequested(paymentId, referenceId, amount);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void autoAuthorizingGatewayGrantsTheHold() {
    configure(new AutoAuthorizingPaymentGateway(commandBus));

    commandBus.send(new AuthorizePayment(referenceId, amount));

    published.shouldReceiveMessages(requested, new PaymentAuthorized(paymentId, now, expiresAt));
  }

  @Test
  public void autoRejectingGatewayDeclinesTheRequestedAuthorization() {
    configure(new AutoRejectingPaymentGateway(commandBus));

    commandBus.send(new AuthorizePayment(referenceId, amount));

    published.shouldReceiveMessages(
      requested,
      new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now)
    );
  }

  @Test
  public void unreachableGatewayDeclinesThePaymentInsteadOfThrowing() {
    configure(new ThrowingPaymentGateway());

    assertThatCode(() -> commandBus.send(new AuthorizePayment(referenceId, amount)))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(
      requested,
      new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now)
    );
  }

  @Test
  public void silentGatewayLeavesTheAuthorizationPending() {
    configure(new SilentPaymentGateway());

    commandBus.send(new AuthorizePayment(referenceId, amount));

    published.shouldReceiveMessages(requested);
  }

  @Test
  public void gatewayIsAuthorizedWithThePaymentIdAndAmount() {
    var gateway = new RecordingPaymentGateway(commandBus);
    configure(gateway);

    commandBus.send(new AuthorizePayment(referenceId, amount));

    assertThat(gateway.calls).containsExactly("authorize:%s:%s".formatted(paymentId, amount));
  }

  @Test
  public void gatewayThatRefusesTheVoidLeavesTheRecordedOutcomeAloneInsteadOfThrowing() {
    configure(new RefusingAfterAuthorizationGateway(commandBus));

    commandBus.send(new AuthorizePayment(referenceId, amount));
    published.reset();

    assertThatCode(() -> commandBus.send(new VoidPayment(paymentId)))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(new PaymentVoided(paymentId, now));
  }

  private void configure(PaymentGateway paymentGateway) {
    eventStore.use(published::catchMessage);

    PaymentsConfig.configure(
      commandBus,
      eventStore,
      eventStore,
      new InMemoryEventBus(),
      paymentGateway,
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }

  // Grants the hold, then refuses every call that follows it.
  private static class RefusingAfterAuthorizationGateway implements PaymentGateway {
    private final InMemoryCommandBus commandBus;

    private RefusingAfterAuthorizationGateway(InMemoryCommandBus commandBus) {
      this.commandBus = commandBus;
    }

    @Override
    public void authorize(PaymentId paymentId, double amount) {
      commandBus.send(new ConfirmPaymentAuthorization(paymentId));
    }

    @Override
    public void capture(PaymentId paymentId, double amount) {
      throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void voidAuthorization(PaymentId paymentId) {
      throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void refund(PaymentId paymentId) {
      throw new RuntimeException("Payment provider is unreachable");
    }
  }
}
