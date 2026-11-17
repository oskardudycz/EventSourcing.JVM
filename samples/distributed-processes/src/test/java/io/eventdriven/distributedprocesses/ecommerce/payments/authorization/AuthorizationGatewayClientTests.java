package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
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

public class AuthorizationGatewayClientTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime captureBefore = now.plus(authorizationValidity);
  private final OffsetDateTime timedOutAt = now.plus(paymentTimeout);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher published = new MessageCatcher();

  @Test
  public void requestingAnAuthorizationAsksTheGatewayForNothingUntilTheCustomerPays() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);

    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    published.shouldReceiveMessages(
      new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timedOutAt)
    );
    assertThat(gateway.calls).isEmpty();
  }

  @Test
  public void canceledPaymentIsCanceledAtTheGateway() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);
    authorize();

    messageBus.publish(List.of(new CancelPayment(paymentId)));

    assertThat(gateway.calls).containsExactly("cancel:%s".formatted(paymentId));
  }

  @Test
  public void gatewayThatRefusesToCancelLeavesTheRecordedOutcomeAloneInsteadOfThrowing() {
    configure(new RefusingAfterAuthorizationPaymentGateway());
    authorize();

    assertThatCode(() -> messageBus.publish(List.of(new CancelPayment(paymentId))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(new PaymentCanceled(paymentId, now));
  }

  @Test
  public void timedOutPaymentExpiresItsCheckoutSessionAtTheGateway() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);

    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new TimeOutPayment(paymentId, timedOutAt)
    ));

    assertThat(gateway.calls).containsExactly("expire-checkout-session:%s".formatted(paymentId));
  }

  @Test
  public void gatewayThatRefusesToExpireTheSessionLeavesTheTimedOutPaymentAloneInsteadOfThrowing() {
    configure(new RefusingAfterAuthorizationPaymentGateway());
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));
    published.reset();

    assertThatCode(() -> messageBus.publish(List.of(new TimeOutPayment(paymentId, timedOutAt))))
      .doesNotThrowAnyException();

    published.shouldReceiveMessages(new PaymentTimedOut(paymentId, timedOutAt));
  }

  private void authorize() {
    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId, captureBefore)
    ));
    published.reset();
  }

  private void configure(PaymentGateway paymentGateway) {
    eventStore.use(published::catchMessage);

    PaymentsConfig.configure(
      eventStore,
      messageBus,
      paymentGateway,
      PaymentValidity.of(paymentTimeout),
      () -> now
    );
  }
}
