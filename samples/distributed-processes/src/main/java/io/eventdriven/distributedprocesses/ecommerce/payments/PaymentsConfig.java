package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.capture.CaptureGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaries;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

public final class PaymentsConfig {
    public static final String requests = "payments-requests";
    public static final String responses = "payments-responses";
    public static final String internal = "payments-internal";

    private PaymentsConfig() {}

    public static PaymentsModule configure(
            EventStore eventStore,
            MessageBus messageBus,
            PaymentGateway paymentGateway,
            PaymentValidity paymentValidity,
            Supplier<OffsetDateTime> now) {
        var store = new AggregateStore<Payment, PaymentEvent, PaymentId>(
                eventStore, PaymentFacade::mapToStreamId, Payment::empty);

        var facade = new PaymentFacade(store, paymentGateway, paymentValidity, now);

        messageBus
                .routes(new MessageRoutes()
                        .routeTarget(AuthorizePayment.class, command -> requests)
                        .routeTarget(CapturePayment.class, command -> requests)
                        .routeTarget(CancelPayment.class, command -> requests)
                        .route(
                                PaymentExternalEvent.class,
                                event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId())))
                        .routeTarget(MessageRoutes.inPackage(PaymentsConfig.class.getPackageName()), internal))
                .bind(requests, requests, "#")
                .bind(internal, internal, "#")
                .subscribe(requests, AuthorizePayment.class, facade::authorizePayment)
                .subscribe(requests, CapturePayment.class, facade::capturePayment)
                .subscribe(requests, CancelPayment.class, facade::cancelPayment)
                .subscribe(internal, ConfirmPaymentAuthorization.class, facade::confirmPaymentAuthorization)
                .subscribe(internal, ConfirmPaymentCapture.class, facade::confirmPaymentCapture)
                .subscribe(internal, FailPaymentCapture.class, facade::failPaymentCapture)
                .subscribe(internal, TimeOutPayment.class, facade::timeOutPayment)
                .subscribe(internal, ExpirePaymentAuthorization.class, facade::expirePaymentAuthorization);

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
                .subscribe(PaymentTimedOut.class, forwarder::onTimedOut)
                .subscribe(PaymentAuthorizationExpired.class, forwarder::onAuthorizationExpired);

        var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway);
        var captureGatewayClient = new CaptureGatewayClient(paymentGateway, messageBus);

        eventStore
                .subscribe(PaymentCaptureRequested.class, envelope -> captureGatewayClient.on(envelope.data()))
                .subscribe(PaymentCanceled.class, envelope -> authorizationGatewayClient.on(envelope.data()))
                .subscribe(PaymentTimedOut.class, envelope -> authorizationGatewayClient.on(envelope.data()));

        return new PaymentsModule(
                facade,
                new ScheduledPaymentTimeoutWorker(new PaymentTimeoutWorker(pendingPayments, messageBus), now),
                new ScheduledAuthorizationExpiryWorker(
                        new AuthorizationExpiryWorker(authorizedPayments, messageBus), now));
    }
}
