package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.DeclinePayment;

// Stands in for a provider that refuses the hold and says so straight away.
public class AutoRejectingPaymentGateway implements PaymentGateway {
  private final MessageBus messageBus;

  public AutoRejectingPaymentGateway(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    messageBus.publish(List.of(new DeclinePayment(paymentId, DeclineReason.UnexpectedError)));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
  }

  @Override
  public void refund(PaymentId paymentId) {
  }
}
