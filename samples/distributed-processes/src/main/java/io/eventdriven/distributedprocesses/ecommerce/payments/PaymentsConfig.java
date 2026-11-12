package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.capture.CaptureGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaries;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public final class PaymentsConfig {
  private PaymentsConfig() {
  }

  public static PaymentsModule configure(
    EventStore eventStore,
    MessageBus messageBus,
    PaymentGateway paymentGateway,
    Duration paymentTimeout,
    Duration authorizationValidity,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Payment, PaymentEvent, PaymentId>(
      eventStore,
      PaymentFacade::mapToStreamId,
      Payment::empty
    );

    var facade = new PaymentFacade(store, authorizationValidity, now);

    messageBus
      .subscribe(AuthorizePayment.class, facade::authorizePayment)
      .subscribe(ConfirmPaymentAuthorization.class, facade::confirmPaymentAuthorization)
      .subscribe(CapturePayment.class, facade::capturePayment)
      .subscribe(VoidPayment.class, facade::voidPayment)
      .subscribe(RefundPayment.class, facade::refundPayment)
      .subscribe(DeclinePayment.class, facade::declinePayment)
      .subscribe(TimeOutPayment.class, facade::timeOutPayment)
      .subscribe(ExpirePaymentAuthorization.class, facade::expirePaymentAuthorization);

    var database = new Database();
    var paymentSummaries = new PaymentSummaries(database);
    var pendingPayments = new PendingPayments(database, now);
    var authorizedPayments = new AuthorizedPayments(database);

    eventStore
      .subscribe(PaymentAuthorizationRequested.class, paymentSummaries::onAuthorizationRequested)
      .subscribe(PaymentAuthorized.class, paymentSummaries::onAuthorized)
      .subscribe(PaymentCaptured.class, paymentSummaries::onCaptured)
      .subscribe(PaymentVoided.class, paymentSummaries::onVoided)
      .subscribe(PaymentRefunded.class, paymentSummaries::onRefunded)
      .subscribe(PaymentDeclined.class, paymentSummaries::onDeclined)
      .subscribe(PaymentTimedOut.class, paymentSummaries::onTimedOut)
      .subscribe(PaymentAuthorizationExpired.class, paymentSummaries::onAuthorizationExpired);

    eventStore
      .subscribe(PaymentAuthorizationRequested.class, pendingPayments::onAuthorizationRequested)
      .subscribe(PaymentAuthorized.class, pendingPayments::onAuthorized)
      .subscribe(PaymentDeclined.class, pendingPayments::onDeclined)
      .subscribe(PaymentTimedOut.class, pendingPayments::onTimedOut);

    eventStore
      .subscribe(PaymentAuthorized.class, authorizedPayments::onAuthorized)
      .subscribe(PaymentCaptured.class, authorizedPayments::onCaptured)
      .subscribe(PaymentVoided.class, authorizedPayments::onVoided)
      .subscribe(PaymentAuthorizationExpired.class, authorizedPayments::onAuthorizationExpired);

    var forwarder = new PaymentExternalEventForwarder(paymentSummaries, messageBus);

    eventStore
      .subscribe(PaymentAuthorized.class, forwarder::onAuthorized)
      .subscribe(PaymentCaptured.class, forwarder::onCaptured)
      .subscribe(PaymentDeclined.class, forwarder::onDeclined)
      .subscribe(PaymentTimedOut.class, forwarder::onTimedOut)
      .subscribe(PaymentAuthorizationExpired.class, forwarder::onAuthorizationExpired);

    var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway, messageBus);
    var captureGatewayClient = new CaptureGatewayClient(paymentGateway);

    eventStore
      .subscribe(PaymentAuthorizationRequested.class, envelope -> authorizationGatewayClient.on(envelope.data()))
      .subscribe(PaymentCaptured.class, envelope -> captureGatewayClient.on(envelope.data()))
      .subscribe(PaymentVoided.class, envelope -> authorizationGatewayClient.on(envelope.data()))
      .subscribe(PaymentRefunded.class, envelope -> captureGatewayClient.on(envelope.data()));

    return new PaymentsModule(
      facade,
      new PaymentTimeoutWorker(pendingPayments, messageBus, paymentTimeout),
      new AuthorizationExpiryWorker(authorizedPayments, messageBus)
    );
  }
}
