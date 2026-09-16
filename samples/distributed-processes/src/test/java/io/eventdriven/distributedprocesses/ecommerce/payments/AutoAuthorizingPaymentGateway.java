package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;

// Stands in for a provider that grants the hold and calls back straight away.
public class AutoAuthorizingPaymentGateway implements PaymentGateway {
  private final CommandBus commandBus;

  public AutoAuthorizingPaymentGateway(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    commandBus.send(new ConfirmPaymentAuthorization(paymentId));
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
