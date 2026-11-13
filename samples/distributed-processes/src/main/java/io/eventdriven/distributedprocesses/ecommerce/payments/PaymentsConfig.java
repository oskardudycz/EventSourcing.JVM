package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
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
      .subscribe(ConfirmPaymentCapture.class, facade::confirmPaymentCapture)
      .subscribe(FailPaymentCapture.class, facade::failPaymentCapture)
      .subscribe(VoidPayment.class, facade::voidPayment)
      .subscribe(DeclinePayment.class, facade::declinePayment)
      .subscribe(TimeOutPayment.class, facade::timeOutPayment)
      .subscribe(ExpirePaymentAuthorization.class, facade::expirePaymentAuthorization);

    var database = new Database();
    var paymentSummaries = new PaymentSummaries(database);
    var pendingPayments = new PendingPayments(database);
    var authorizedPayments = new AuthorizedPayments(database);

    for (var eventType : paymentSummaries.canHandle()) {
      eventStore.subscribe(eventType, paymentSummaries::handle);
    }

    for (var eventType : pendingPayments.canHandle()) {
      eventStore.subscribe(eventType, pendingPayments::handle);
    }

    for (var eventType : authorizedPayments.canHandle()) {
      eventStore.subscribe(eventType, authorizedPayments::handle);
    }

    var forwarder = new PaymentExternalEventForwarder(paymentSummaries, messageBus);

    eventStore
      .subscribe(PaymentAuthorized.class, forwarder::onAuthorized)
      .subscribe(PaymentCaptured.class, forwarder::onCaptured)
      .subscribe(PaymentCaptureFailed.class, forwarder::onCaptureFailed)
      .subscribe(PaymentDeclined.class, forwarder::onDeclined)
      .subscribe(PaymentTimedOut.class, forwarder::onTimedOut)
      .subscribe(PaymentAuthorizationExpired.class, forwarder::onAuthorizationExpired);

    var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway, messageBus);
    var captureGatewayClient = new CaptureGatewayClient(paymentGateway, messageBus);

    eventStore
      .subscribe(PaymentAuthorizationRequested.class, envelope -> authorizationGatewayClient.on(envelope.data()))
      .subscribe(PaymentCaptureRequested.class, envelope -> captureGatewayClient.on(envelope.data()))
      .subscribe(PaymentVoided.class, envelope -> authorizationGatewayClient.on(envelope.data()))
      .subscribe(PaymentTimedOut.class, envelope -> authorizationGatewayClient.on(envelope.data()));

    return new PaymentsModule(
      facade,
      new ScheduledPaymentTimeoutWorker(new PaymentTimeoutWorker(pendingPayments, messageBus, paymentTimeout), now),
      new ScheduledAuthorizationExpiryWorker(new AuthorizationExpiryWorker(authorizedPayments, messageBus), now)
    );
  }
}
