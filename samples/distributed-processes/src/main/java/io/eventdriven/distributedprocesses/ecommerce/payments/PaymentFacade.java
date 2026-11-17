package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;

public class PaymentFacade {
  private static final Duration checkoutSessionLifetime = Duration.ofMinutes(30);

  private final AggregateStore<Payment, PaymentEvent, PaymentId> store;
  private final PaymentGateway paymentGateway;
  private final PaymentValidity paymentValidity;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(PaymentId paymentId) {
    return "Payment-%s".formatted(paymentId.tail());
  }

  public PaymentFacade(
    AggregateStore<Payment, PaymentEvent, PaymentId> store,
    PaymentGateway paymentGateway,
    PaymentValidity paymentValidity,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.paymentGateway = paymentGateway;
    this.paymentValidity = paymentValidity;
    this.now = now;
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.requests, description = "Records a payment that waits for the customer to authorize it on the payment provider's page."))
  public void authorizePayment(AuthorizePayment command) {
    var requestedAt = now.get();

    store.getAndUpdate(
      command.paymentId(),
      current -> current.requestAuthorization(
        command.paymentId(),
        command.referenceId(),
        command.amount(),
        requestedAt,
        paymentValidity.timesOutAt(requestedAt)
      )
    );
  }

  public CheckoutSession createCheckoutSession(CreateCheckoutSession command) {
    var createdAt = now.get();
    var payment = store.get(command.paymentId())
      .filter(found -> found.referenceId().equals(command.referenceId()))
      .orElseThrow(() -> new NoSuchElementException(
        "Payment '%s' for '%s' was not found".formatted(command.paymentId().value(), command.referenceId())
      ));

    if (!payment.isPending())
      throw new IllegalStateException("Payment '%s' is not pending".formatted(command.paymentId().value()));

    var openSession = payment.openCheckoutSession(createdAt);
    if (openSession.isPresent())
      return openSession.get();

    CheckoutSession session;
    try {
      session = paymentGateway.createCheckoutSession(
        command.paymentId(),
        payment.amount(),
        command.successUrl(),
        command.cancelUrl(),
        createdAt.plus(checkoutSessionLifetime)
      );
    } catch (RuntimeException failedCall) {
      throw new PaymentGatewayException(
        "Opening a checkout session for payment '%s' failed".formatted(command.paymentId().value()),
        failedCall
      );
    }

    store.getAndUpdate(
      command.paymentId(),
      current -> current.recordCheckoutSession(session, createdAt)
    );

    return session;
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.internal, description = "Records that the payment is authorized, and the date by which the shop must charge the customer."))
  public void confirmPaymentAuthorization(ConfirmPaymentAuthorization command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.confirmAuthorization(now.get(), command.captureBefore())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.requests, description = "Asks the payment provider to charge the customer."))
  public void capturePayment(CapturePayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.requestCapture(now.get())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.internal, description = "Records that the customer was charged."))
  public void confirmPaymentCapture(ConfirmPaymentCapture command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.confirmCapture(now.get())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.internal, description = "Records that charging the customer failed."))
  public void failPaymentCapture(FailPaymentCapture command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.failCapture(command.reason(), now.get())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.requests, description = "Cancels the authorized payment, so the customer is never charged."))
  public void cancelPayment(CancelPayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.cancel(now.get())
    );
  }


  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.internal, description = "Fails the payment, because the customer did not pay in time."))
  public void timeOutPayment(TimeOutPayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.timeOut(command.timedOutAt())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = PaymentsConfig.internal, description = "Fails the payment, because its authorization ran out before the shop charged the customer."))
  public void expirePaymentAuthorization(ExpirePaymentAuthorization command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.expireAuthorization(command.expiredAt())
    );
  }
}
