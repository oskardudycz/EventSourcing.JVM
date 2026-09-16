package io.eventdriven.distributedprocesses.ecommerce.payments;


// Stands in for a provider that has taken the authorisation but not answered it yet.
public class SilentPaymentGateway implements PaymentGateway {
  @Override
  public void authorize(PaymentId paymentId, double amount) {
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
