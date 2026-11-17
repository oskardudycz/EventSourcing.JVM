package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

// Stands in for a provider that opens checkout sessions and has taken the capture but not answered it yet.
public class SilentPaymentGateway implements PaymentGateway {
  public final List<PaymentId> checkoutSessionsCreated = new ArrayList<>();
  public final List<PaymentId> canceled = new ArrayList<>();
  public final List<PaymentId> expiredCheckoutSessions = new ArrayList<>();

  @Override
  public CheckoutSession createCheckoutSession(
    PaymentId paymentId,
    double amount,
    URI successUrl,
    URI cancelUrl,
    OffsetDateTime expiresAt
  ) {
    checkoutSessionsCreated.add(paymentId);
    return new CheckoutSession(URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
  }

  @Override
  public void expireCheckoutSession(PaymentId paymentId) {
    expiredCheckoutSessions.add(paymentId);
  }

  @Override
  public void capture(PaymentId paymentId, double amount) {
  }

  @Override
  public void cancel(PaymentId paymentId) {
    canceled.add(paymentId);
  }
}
