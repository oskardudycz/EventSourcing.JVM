package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;

import java.util.ArrayList;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;

// Grants the hold like the auto-authorising double, and writes down every call it was given.
public class RecordingPaymentGateway implements PaymentGateway {
  public final List<String> calls = new ArrayList<>();

  private final CommandBus commandBus;

  public RecordingPaymentGateway(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    calls.add("authorize:%s:%s".formatted(paymentId, amount));

    commandBus.send(new ConfirmPaymentAuthorization(paymentId));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    calls.add("capture:%s:%s".formatted(paymentId, amount));
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
    calls.add("void:%s".formatted(paymentId));
  }

  @Override
  public void refund(PaymentId paymentId) {
    calls.add("refund:%s".formatted(paymentId));
  }
}
