package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
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
    CommandBus commandBus,
    EventStore eventStore,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus,
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

    commandBus
      .handle(AuthorizePayment.class, facade::authorizePayment)
      .handle(ConfirmPaymentAuthorization.class, facade::confirmPaymentAuthorization)
      .handle(CapturePayment.class, facade::capturePayment)
      .handle(VoidPayment.class, facade::voidPayment)
      .handle(RefundPayment.class, facade::refundPayment)
      .handle(DeclinePayment.class, facade::declinePayment)
      .handle(TimeOutPayment.class, facade::timeOutPayment)
      .handle(ExpirePaymentAuthorization.class, facade::expirePaymentAuthorization);

    var database = new Database();
    var paymentSummaries = new PaymentSummaries(database);
    var pendingPayments = new PendingPayments(database, now);
    var authorizedPayments = new AuthorizedPayments(database);

    internalEventBus
      .subscribeWithMetadata(PaymentAuthorizationRequested.class, paymentSummaries::onAuthorizationRequested)
      .subscribeWithMetadata(PaymentAuthorized.class, paymentSummaries::onAuthorized)
      .subscribeWithMetadata(PaymentCaptured.class, paymentSummaries::onCaptured)
      .subscribeWithMetadata(PaymentVoided.class, paymentSummaries::onVoided)
      .subscribeWithMetadata(PaymentRefunded.class, paymentSummaries::onRefunded)
      .subscribeWithMetadata(PaymentDeclined.class, paymentSummaries::onDeclined)
      .subscribeWithMetadata(PaymentTimedOut.class, paymentSummaries::onTimedOut)
      .subscribeWithMetadata(PaymentAuthorizationExpired.class, paymentSummaries::onAuthorizationExpired);

    internalEventBus
      .subscribeWithMetadata(PaymentAuthorizationRequested.class, pendingPayments::onAuthorizationRequested)
      .subscribeWithMetadata(PaymentAuthorized.class, pendingPayments::onAuthorized)
      .subscribeWithMetadata(PaymentDeclined.class, pendingPayments::onDeclined)
      .subscribeWithMetadata(PaymentTimedOut.class, pendingPayments::onTimedOut);

    internalEventBus
      .subscribeWithMetadata(PaymentAuthorized.class, authorizedPayments::onAuthorized)
      .subscribeWithMetadata(PaymentCaptured.class, authorizedPayments::onCaptured)
      .subscribeWithMetadata(PaymentVoided.class, authorizedPayments::onVoided)
      .subscribeWithMetadata(PaymentAuthorizationExpired.class, authorizedPayments::onAuthorizationExpired);

    var forwarder = new PaymentExternalEventForwarder(paymentSummaries, integrationEventBus);

    // After the projections, so that it enriches from a summary that already holds the event
    // it forwards, and still before the gateway client, so that settling a payment does not
    // publish its outcome before the request that caused it.
    internalEventBus
      .subscribe(PaymentAuthorized.class, forwarder::on)
      .subscribe(PaymentCaptured.class, forwarder::on)
      .subscribe(PaymentDeclined.class, forwarder::on)
      .subscribe(PaymentTimedOut.class, forwarder::on)
      .subscribe(PaymentAuthorizationExpired.class, forwarder::on);

    var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway, commandBus);
    var captureGatewayClient = new CaptureGatewayClient(paymentGateway);

    // Last, because a provider that answers during the call appends that answer from here:
    // the projections and the forwarder have to be done with the event before that happens.
    internalEventBus
      .subscribe(PaymentAuthorizationRequested.class, authorizationGatewayClient::on)
      .subscribe(PaymentCaptured.class, captureGatewayClient::on)
      .subscribe(PaymentVoided.class, authorizationGatewayClient::on)
      .subscribe(PaymentRefunded.class, captureGatewayClient::on);

    return new PaymentsModule(
      facade,
      new PaymentTimeoutWorker(pendingPayments, commandBus, paymentTimeout),
      new AuthorizationExpiryWorker(authorizedPayments, commandBus)
    );
  }
}
