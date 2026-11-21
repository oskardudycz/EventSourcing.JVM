package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageQueue;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentsConfig {
    public static final String requests = "payments-requests";
    public static final String responses = "payments-responses";
    public static final String internal = "payments-internal";

    @Bean
    public static MessageRoutes paymentsRoutes() {
        return new MessageRoutes()
                .routeTarget(AuthorizePayment.class, command -> requests)
                .routeTarget(CapturePayment.class, command -> requests)
                .routeTarget(CancelPayment.class, command -> requests)
                .route(
                        PaymentExternalEvent.class,
                        event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId())))
                .routeTarget(MessageRoutes.inPackage(PaymentsConfig.class.getPackageName()), internal)
                .routeKey(
                        PaymentCommand.class,
                        command -> Urns.routingKeyOf(command.paymentId().value()));
    }

    @Bean
    public static MessageChannel paymentsRequestsChannel() {
        return new MessageChannel(requests);
    }

    @Bean
    public static MessageChannel paymentsResponsesChannel() {
        return new MessageChannel(responses);
    }

    @Bean
    public static MessageChannel paymentsInternalChannel() {
        return new MessageChannel(internal);
    }

    @Bean
    public static MessageQueue paymentsRequestsQueue() {
        return new MessageQueue(requests, requests, "#");
    }

    @Bean
    public static MessageQueue paymentsInternalQueue() {
        return new MessageQueue(internal, internal, "#");
    }

    @Bean
    public PaymentValidity paymentValidity() {
        return PaymentValidity.of(Duration.ofHours(24));
    }

    @Bean
    public PaymentFacade paymentFacade(
            EventStore eventStore,
            MessageBus messageBus,
            PaymentGateway paymentGateway,
            PaymentValidity paymentValidity,
            Supplier<OffsetDateTime> now) {
        var store = new AggregateStore<Payment, PaymentEvent, PaymentId>(
                eventStore, PaymentFacade::mapToStreamId, Payment::empty);

        var facade = new PaymentFacade(store, paymentGateway, paymentValidity, now);

        messageBus
                .subscribe(requests, AuthorizePayment.class, facade::authorizePayment)
                .subscribe(requests, CapturePayment.class, facade::capturePayment)
                .subscribe(requests, CancelPayment.class, facade::cancelPayment)
                .subscribe(internal, ConfirmPaymentAuthorization.class, facade::confirmPaymentAuthorization)
                .subscribe(internal, ConfirmPaymentCapture.class, facade::confirmPaymentCapture)
                .subscribe(internal, FailPaymentCapture.class, facade::failPaymentCapture)
                .subscribe(internal, TimeOutPayment.class, facade::timeOutPayment)
                .subscribe(internal, ExpirePaymentAuthorization.class, facade::expirePaymentAuthorization);

        return facade;
    }

    @Bean
    public PaymentSummaries paymentSummaries(EventStore eventStore, PaymentSummaryRepository paymentSummaryRepository) {
        var paymentSummaries = new PaymentSummaries(paymentSummaryRepository);

        eventStore.subscribe("payment-summaries", paymentSummaries.canHandle(), paymentSummaries::handle);

        return paymentSummaries;
    }

    @Bean
    public PendingPayments pendingPayments(EventStore eventStore, PendingPaymentRepository pendingPaymentRepository) {
        var pendingPayments = new PendingPayments(pendingPaymentRepository);

        eventStore.subscribe("pending-payments", pendingPayments.canHandle(), pendingPayments::handle);

        return pendingPayments;
    }

    @Bean
    public AuthorizedPayments authorizedPayments(
            EventStore eventStore, AuthorizedPaymentRepository authorizedPaymentRepository) {
        var authorizedPayments = new AuthorizedPayments(authorizedPaymentRepository);

        eventStore.subscribe("authorized-payments", authorizedPayments.canHandle(), authorizedPayments::handle);

        return authorizedPayments;
    }

    @Bean
    public PaymentExternalEventForwarder paymentExternalEventForwarder(
            EventStore eventStore, PaymentSummaries paymentSummaries, MessageBus messageBus) {
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

        return forwarder;
    }

    @Bean
    public CaptureGatewayClient captureGatewayClient(
            EventStore eventStore, PaymentGateway paymentGateway, MessageBus messageBus) {
        var captureGatewayClient = new CaptureGatewayClient(paymentGateway, messageBus);

        eventStore.subscribe(
                "capture-gateway-client",
                List.of(PaymentCaptureRequested.class),
                envelope -> captureGatewayClient.on((PaymentCaptureRequested) envelope.data()));

        return captureGatewayClient;
    }

    @Bean
    public AuthorizationGatewayClient authorizationGatewayClient(EventStore eventStore, PaymentGateway paymentGateway) {
        var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway);

        eventStore.subscribe(
                "authorization-gateway-client", List.of(PaymentCanceled.class, PaymentTimedOut.class), envelope -> {
                    switch (envelope.data()) {
                        case PaymentCanceled event -> authorizationGatewayClient.on(event);
                        case PaymentTimedOut event -> authorizationGatewayClient.on(event);
                        default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
                    }
                });

        return authorizationGatewayClient;
    }

    @Bean
    public ScheduledPaymentTimeoutWorker paymentTimeoutWorker(
            PendingPayments pendingPayments, MessageBus messageBus, Supplier<OffsetDateTime> now) {
        return new ScheduledPaymentTimeoutWorker(new PaymentTimeoutWorker(pendingPayments, messageBus), now);
    }

    @Bean
    public ScheduledAuthorizationExpiryWorker authorizationExpiryWorker(
            AuthorizedPayments authorizedPayments, MessageBus messageBus, Supplier<OffsetDateTime> now) {
        return new ScheduledAuthorizationExpiryWorker(
                new AuthorizationExpiryWorker(authorizedPayments, messageBus), now);
    }
}
