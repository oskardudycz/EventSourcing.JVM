package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.capture.CaptureGatewayClient;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaries;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import java.time.OffsetDateTime;
import java.util.List;
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
            PaymentSummaryRepository paymentSummaryRepository,
            PendingPaymentRepository pendingPaymentRepository,
            AuthorizedPaymentRepository authorizedPaymentRepository,
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
                        .routeTarget(MessageRoutes.inPackage(PaymentsConfig.class.getPackageName()), internal)
                        .routeKey(
                                PaymentCommand.class,
                                command -> Urns.routingKeyOf(command.paymentId().value())))
                .channel(requests)
                .channel(responses)
                .channel(internal)
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

        var paymentSummaries = new PaymentSummaries(paymentSummaryRepository);
        var pendingPayments = new PendingPayments(pendingPaymentRepository);
        var authorizedPayments = new AuthorizedPayments(authorizedPaymentRepository);

        eventStore.subscribe("payment-summaries", paymentSummaries.canHandle(), paymentSummaries::handle);

        eventStore.subscribe("pending-payments", pendingPayments.canHandle(), pendingPayments::handle);

        eventStore.subscribe("authorized-payments", authorizedPayments.canHandle(), authorizedPayments::handle);

        var forwarder = new PaymentExternalEventForwarder(paymentSummaries, messageBus);

        eventStore.subscribe(
                "payments-external-forwarder",
                List.of(
                        PaymentAuthorized.class,
                        PaymentCaptured.class,
                        PaymentCaptureFailed.class,
                        PaymentTimedOut.class,
                        PaymentAuthorizationExpired.class),
                forwarder::handle);

        var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway);
        var captureGatewayClient = new CaptureGatewayClient(paymentGateway, messageBus);

        eventStore.subscribe(
                "capture-gateway-client",
                List.of(PaymentCaptureRequested.class),
                envelope -> captureGatewayClient.on((PaymentCaptureRequested) envelope.data()));

        eventStore.subscribe(
                "authorization-gateway-client", List.of(PaymentCanceled.class, PaymentTimedOut.class), envelope -> {
                    switch (envelope.data()) {
                        case PaymentCanceled event -> authorizationGatewayClient.on(event);
                        case PaymentTimedOut event -> authorizationGatewayClient.on(event);
                        default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
                    }
                });

        return new PaymentsModule(
                facade,
                new ScheduledPaymentTimeoutWorker(new PaymentTimeoutWorker(pendingPayments, messageBus), now),
                new ScheduledAuthorizationExpiryWorker(
                        new AuthorizationExpiryWorker(authorizedPayments, messageBus), now));
    }
}
