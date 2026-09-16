package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEventForwarder;

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

    var forwarder = new PaymentExternalEventForwarder(store, integrationEventBus);

    // Forwarding first, so that settling a payment does not publish its outcome
    // before the request that caused it.
    internalEventBus
      .subscribe(PaymentAuthorized.class, forwarder::on)
      .subscribe(PaymentCaptured.class, forwarder::on)
      .subscribe(PaymentDeclined.class, forwarder::on)
      .subscribe(PaymentTimedOut.class, forwarder::on)
      .subscribe(PaymentAuthorizationExpired.class, forwarder::on);

    var pendingPayments = new PendingPayments(now);

    internalEventBus
      .subscribe(PaymentAuthorizationRequested.class, pendingPayments::on)
      .subscribe(PaymentAuthorized.class, pendingPayments::on)
      .subscribe(PaymentDeclined.class, pendingPayments::on)
      .subscribe(PaymentTimedOut.class, pendingPayments::on);

    var authorizedPayments = new AuthorizedPayments();

    internalEventBus
      .subscribe(PaymentAuthorized.class, authorizedPayments::on)
      .subscribe(PaymentCaptured.class, authorizedPayments::on)
      .subscribe(PaymentVoided.class, authorizedPayments::on)
      .subscribe(PaymentAuthorizationExpired.class, authorizedPayments::on);

    var paymentGatewayClient = new PaymentGatewayClient(paymentGateway, commandBus);

    internalEventBus
      .subscribe(PaymentAuthorizationRequested.class, paymentGatewayClient::on)
      .subscribe(PaymentCaptured.class, paymentGatewayClient::on)
      .subscribe(PaymentVoided.class, paymentGatewayClient::on)
      .subscribe(PaymentRefunded.class, paymentGatewayClient::on);

    return new PaymentsModule(
      facade,
      new PaymentTimeoutWorker(pendingPayments, commandBus, paymentTimeout),
      new AuthorizationExpiryWorker(authorizedPayments, commandBus)
    );
  }
}
