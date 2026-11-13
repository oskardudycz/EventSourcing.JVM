package io.eventdriven.distributedprocesses.ecommerce.payments;


// A real provider would only accept the authorisation or the capture here and confirm it later
// through a webhook, which in this sample is modelled by the implementation sending a command back.
// Voiding does not answer back.
public interface PaymentGateway {
  void authorize(PaymentId paymentId, double amount);

  void capture(PaymentId paymentId, double amount);

  void voidAuthorization(PaymentId paymentId);
}
