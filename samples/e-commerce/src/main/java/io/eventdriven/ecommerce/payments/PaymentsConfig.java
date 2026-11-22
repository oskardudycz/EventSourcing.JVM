package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.core.commands.CommandHandler;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.ecommerce.payments.authorization.AuthorizationGatewayClient;
import io.eventdriven.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.ecommerce.payments.authorization.AuthorizedPayments;
import io.eventdriven.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.ecommerce.payments.capture.CaptureGatewayClient;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.ecommerce.payments.external.PaymentSummaries;
import io.eventdriven.ecommerce.payments.external.PaymentSummaryRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentsConfig {
    public static final String requests = "payments-requests";
    public static final String responses = "payments-responses";
    public static final String internal = "payments-internal";

    @Bean("paymentsMapping")
    public static MessageMapping mapping() {
        return new MessageMapping()
                .trust(PaymentsConfig.class.getPackageName(), PaymentExternalEvent.class.getPackageName());
    }

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
    public Payment.Validity paymentValidity() {
        return Payment.Validity.of(Duration.ofHours(24));
    }

    @Bean
    public PaymentFacade paymentFacade(
            EventStore eventStore,
            MessageContext messageContext,
            MessageBus messageBus,
            PaymentGateway paymentGateway,
            Payment.Validity paymentValidity,
            Supplier<OffsetDateTime> now) {
        var commandHandler = new CommandHandler<Payment.Id, Payment, PaymentEvent>(
                eventStore, messageContext, PaymentFacade::mapToStreamId, Payment::initial, Payment::evolve);

        var facade = new PaymentFacade(commandHandler, paymentValidity, paymentGateway, now);

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

        return paymentSummaries;
    }

    @Bean
    public PendingPayments pendingPayments(EventStore eventStore, PendingPaymentRepository pendingPaymentRepository) {
        var pendingPayments = new PendingPayments(pendingPaymentRepository);

        eventStore.subscribe("pending-payments", pendingPayments);

        return pendingPayments;
    }

    @Bean
    public AuthorizedPayments authorizedPayments(
            EventStore eventStore, AuthorizedPaymentRepository authorizedPaymentRepository) {
        var authorizedPayments = new AuthorizedPayments(authorizedPaymentRepository);

        eventStore.subscribe("authorized-payments", authorizedPayments);

        return authorizedPayments;
    }

    @Bean
    public PaymentExternalEventForwarder paymentExternalEventForwarder(
            EventStore eventStore, PaymentSummaries paymentSummaries, MessageBus messageBus) {
        var forwarder = new PaymentExternalEventForwarder(paymentSummaries, messageBus);

        eventStore.subscribe("payments-external", paymentSummaries, forwarder);

        return forwarder;
    }

    @Bean
    public CaptureGatewayClient captureGatewayClient(
            EventStore eventStore, PaymentGateway paymentGateway, MessageBus messageBus) {
        var captureGatewayClient = new CaptureGatewayClient(paymentGateway, messageBus);

        eventStore.subscribe("capture-gateway-client", captureGatewayClient);

        return captureGatewayClient;
    }

    @Bean
    public AuthorizationGatewayClient authorizationGatewayClient(EventStore eventStore, PaymentGateway paymentGateway) {
        var authorizationGatewayClient = new AuthorizationGatewayClient(paymentGateway);

        eventStore.subscribe("authorization-gateway-client", authorizationGatewayClient);

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
