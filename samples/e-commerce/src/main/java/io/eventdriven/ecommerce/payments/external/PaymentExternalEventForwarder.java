package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.*;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Forwarder;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.time.OffsetDateTime;
import java.util.List;

public class PaymentExternalEventForwarder implements Forwarder {
    private final PaymentSummaries summaries;
    private final MessageBus messageBus;

    public PaymentExternalEventForwarder(PaymentSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                PaymentEvent.PaymentAuthorized.class,
                PaymentEvent.PaymentCaptured.class,
                PaymentCaptureFailed.class,
                PaymentTimedOut.class,
                PaymentAuthorizationExpired.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        switch (envelope.message()) {
            case PaymentEvent.PaymentAuthorized event -> onAuthorized(new MessageEnvelope<>(event, metadata));
            case PaymentEvent.PaymentCaptured event -> onCaptured(new MessageEnvelope<>(event, metadata));
            case PaymentCaptureFailed event -> onCaptureFailed(new MessageEnvelope<>(event, metadata));
            case PaymentTimedOut event -> onTimedOut(new MessageEnvelope<>(event, metadata));
            case PaymentAuthorizationExpired event -> onAuthorizationExpired(new MessageEnvelope<>(event, metadata));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.message()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentExternalEvent.PaymentAuthorized.class,
                            description = "Tells the requester that the payment is authorized."))
    public void onAuthorized(MessageEnvelope<PaymentEvent.PaymentAuthorized> envelope) {
        var event = envelope.message();

        messageBus.publish(List.of(new PaymentExternalEvent.PaymentAuthorized(
                referenceIdOf(event.paymentId()), event.paymentId(), event.authorizedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentExternalEvent.PaymentCaptured.class,
                            description = "Tells the requester that the customer was charged."))
    public void onCaptured(MessageEnvelope<PaymentEvent.PaymentCaptured> envelope) {
        var event = envelope.message();

        messageBus.publish(List.of(new PaymentExternalEvent.PaymentCaptured(
                referenceIdOf(event.paymentId()), event.paymentId(), event.capturedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onTimedOut(MessageEnvelope<PaymentTimedOut> envelope) {
        publishFailure(envelope.message().paymentId(), envelope.message().timedOutAt());
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onAuthorizationExpired(MessageEnvelope<PaymentAuthorizationExpired> envelope) {
        publishFailure(envelope.message().paymentId(), envelope.message().expiredAt());
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.responses,
                            payloadType = PaymentFailed.class,
                            description = "Tells the requester that the payment failed."))
    public void onCaptureFailed(MessageEnvelope<PaymentCaptureFailed> envelope) {
        publishFailure(envelope.message().paymentId(), envelope.message().failedAt());
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
