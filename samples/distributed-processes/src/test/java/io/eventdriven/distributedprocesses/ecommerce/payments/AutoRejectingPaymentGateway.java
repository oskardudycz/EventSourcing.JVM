package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.DeclinePayment;

// Stands in for a provider that refuses the hold and says so straight away.
public class AutoRejectingPaymentGateway implements PaymentGateway {
  private final CommandBus commandBus;

  public AutoRejectingPaymentGateway(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    commandBus.send(new DeclinePayment(paymentId, DeclineReason.UnexpectedError));
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
