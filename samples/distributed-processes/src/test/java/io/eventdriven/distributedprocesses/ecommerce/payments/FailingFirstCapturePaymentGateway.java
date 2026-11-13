package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

// Grants every hold, refuses the first capture, then grants the captures that follow.
public class FailingFirstCapturePaymentGateway implements PaymentGateway {
  private final MessageBus messageBus;
  private boolean refusedOnce;

  public FailingFirstCapturePaymentGateway(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    if (!refusedOnce) {
      refusedOnce = true;
      throw new RuntimeException("Payment provider is unreachable");
    }

    messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
  }
}
