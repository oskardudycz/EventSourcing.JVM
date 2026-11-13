package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.util.ArrayList;
import java.util.List;

// Stands in for a provider that has taken the authorisation or the capture but not answered it yet.
public class SilentPaymentGateway implements PaymentGateway {
  public final List<PaymentId> voided = new ArrayList<>();

  @Override
  public void authorize(PaymentId paymentId, double amount) {
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
    voided.add(paymentId);
  }
}
