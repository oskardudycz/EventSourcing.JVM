package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.*;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.time.OffsetDateTime;
import java.util.List;

public class PaymentExternalEventForwarder {
    private final PaymentSummaries summaries;
    private final MessageBus messageBus;

    public PaymentExternalEventForwarder(PaymentSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    public void handle(EventEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        switch (envelope.data()) {
            case PaymentEvent.PaymentAuthorized event -> onAuthorized(new EventEnvelope<>(event, metadata));
            case PaymentEvent.PaymentCaptured event -> onCaptured(new EventEnvelope<>(event, metadata));
            case PaymentCaptureFailed event -> onCaptureFailed(new EventEnvelope<>(event, metadata));
            case PaymentTimedOut event -> onTimedOut(new EventEnvelope<>(event, metadata));
            case PaymentAuthorizationExpired event -> onAuthorizationExpired(new EventEnvelope<>(event, metadata));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentExternalEvent.PaymentAuthorized.class,
                            description = "Tells the requester that the payment is authorized."))
    public void onAuthorized(EventEnvelope<PaymentEvent.PaymentAuthorized> envelope) {
        var event = envelope.data();

        messageBus.publish(List.of(new PaymentExternalEvent.PaymentAuthorized(
                referenceIdOf(event.paymentId()), event.paymentId(), event.authorizedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentExternalEvent.PaymentCaptured.class,
                            description = "Tells the requester that the customer was charged."))
    public void onCaptured(EventEnvelope<PaymentEvent.PaymentCaptured> envelope) {
        var event = envelope.data();

        messageBus.publish(List.of(new PaymentExternalEvent.PaymentCaptured(
                referenceIdOf(event.paymentId()), event.paymentId(), event.capturedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onTimedOut(EventEnvelope<PaymentTimedOut> envelope) {
        publishFailure(envelope.data().paymentId(), envelope.data().timedOutAt());
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onAuthorizationExpired(EventEnvelope<PaymentAuthorizationExpired> envelope) {
        publishFailure(envelope.data().paymentId(), envelope.data().expiredAt());
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onCaptureFailed(EventEnvelope<PaymentCaptureFailed> envelope) {
        publishFailure(envelope.data().paymentId(), envelope.data().failedAt());
    }

    private void publishFailure(Payment.Id paymentId, OffsetDateTime failedAt) {
        messageBus.publish(List.of(new PaymentFailed(referenceIdOf(paymentId), paymentId, failedAt)));
    }

    private String referenceIdOf(Payment.Id paymentId) {
        return summaries
                .get(paymentId)
                .orElseThrow(
                        () -> new IllegalStateException("Payment '%s' has no summary yet".formatted(paymentId.value())))
                .getReferenceId();
    }
}
