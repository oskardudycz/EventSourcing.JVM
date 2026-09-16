package io.eventdriven.distributedprocesses.ecommerce.payments;


// Stands in for a provider that cannot be reached at all.
public class ThrowingPaymentGateway implements PaymentGateway {
  @Override
  public void authorize(PaymentId paymentId, double amount) {
    throw new RuntimeException("Payment provider is unreachable");
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
    throw new RuntimeException("Payment provider is unreachable");
  }

  @Override
  public void voidAuthorization(PaymentId paymentId) {
    throw new RuntimeException("Payment provider is unreachable");
  }

  @Override
  public void refund(PaymentId paymentId) {
    throw new RuntimeException("Payment provider is unreachable");
  }
}
