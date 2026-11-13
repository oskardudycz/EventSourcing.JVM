package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.util.ArrayList;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

// Grants the hold and the capture like the auto-authorising double, and writes down every call it was given.
public class RecordingPaymentGateway implements PaymentGateway {
  public final List<String> calls = new ArrayList<>();

  private final MessageBus messageBus;

  public RecordingPaymentGateway(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  @Override
  public void authorize(PaymentId paymentId, double amount) {
    calls.add("authorize:%s:%s".formatted(paymentId, amount));

    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId)));
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    calls.add("capture:%s:%s".formatted(paymentId, amount));

    messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
    calls.add("void:%s".formatted(paymentId));
  }
}
