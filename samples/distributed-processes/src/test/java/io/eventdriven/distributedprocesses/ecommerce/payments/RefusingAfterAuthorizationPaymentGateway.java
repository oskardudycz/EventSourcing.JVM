package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;

// Grants the hold, then refuses every call that follows it.
public class RefusingAfterAuthorizationPaymentGateway implements PaymentGateway {
  private final MessageBus messageBus;

  public RefusingAfterAuthorizationPaymentGateway(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    throw new RuntimeException("Payment provider is unreachable");
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
    throw new RuntimeException("Payment provider is unreachable");
  }
}
