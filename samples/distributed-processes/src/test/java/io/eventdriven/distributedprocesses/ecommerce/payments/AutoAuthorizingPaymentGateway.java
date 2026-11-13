package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

// Stands in for a provider that grants the hold and the capture, and calls back straight away.
public class AutoAuthorizingPaymentGateway implements PaymentGateway {
  private final MessageBus messageBus;

  public AutoAuthorizingPaymentGateway(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
  }
}
