package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;

public class PaymentFacade {
  private final AggregateStore<Payment, PaymentEvent, PaymentId> store;
  private final Duration authorizationValidity;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(PaymentId paymentId) {
    return "Payment-%s".formatted(paymentId.tail());
  }

  public PaymentFacade(
    AggregateStore<Payment, PaymentEvent, PaymentId> store,
    Duration authorizationValidity,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.authorizationValidity = authorizationValidity;
    this.now = now;
  }

  public void authorizePayment(AuthorizePayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.requestAuthorization(
        command.paymentId(),
        command.referenceId(),
        command.amount(),
        now.get()
      )
    );
  }

  public void confirmPaymentAuthorization(ConfirmPaymentAuthorization command) {
    var authorizedAt = now.get();

    store.getAndUpdate(
      command.paymentId(),
      current -> current.confirmAuthorization(
        authorizedAt,
        authorizedAt.plus(authorizationValidity)
      )
    );
  }

  public void capturePayment(CapturePayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.requestCapture(now.get())
    );
  }

  public void confirmPaymentCapture(ConfirmPaymentCapture command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.confirmCapture(now.get())
    );
  }

  public void failPaymentCapture(FailPaymentCapture command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.failCapture(command.reason(), now.get())
    );
  }

  public void voidPayment(VoidPayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.voidAuthorization(now.get())
    );
  }

  public void declinePayment(DeclinePayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.decline(command.reason(), now.get())
    );
  }

  public void timeOutPayment(TimeOutPayment command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.timeOut(command.timedOutAt())
    );
  }

  public void expirePaymentAuthorization(ExpirePaymentAuthorization command) {
    store.getAndUpdate(
      command.paymentId(),
      current -> current.expireAuthorization(command.expiredAt())
    );
  }
}
