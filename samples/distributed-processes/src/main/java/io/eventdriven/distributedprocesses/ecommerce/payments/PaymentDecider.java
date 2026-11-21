package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.payments.Payment.Status;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

public class PaymentDecider {
    private static final PaymentEvent[] noEvents = new PaymentEvent[0];

    private final PaymentValidity paymentValidity;
    private final Supplier<OffsetDateTime> now;

    public PaymentDecider(PaymentValidity paymentValidity, Supplier<OffsetDateTime> now) {
        this.paymentValidity = paymentValidity;
        this.now = now;
    }

    public PaymentEvent[] handle(PaymentCommand command, Payment state) {
        return switch (command) {
            case AuthorizePayment authorize -> requestAuthorization(authorize, state);
            case RecordCheckoutSession record -> recordCheckoutSession(record, state);
            case ConfirmPaymentAuthorization confirm -> confirmAuthorization(confirm, state);
            case CapturePayment ignored -> requestCapture(state);
            case ConfirmPaymentCapture ignored -> confirmCapture(state);
            case FailPaymentCapture fail -> failCapture(fail, state);
            case CancelPayment ignored -> cancel(state);
            case TimeOutPayment timeOut -> timeOut(timeOut, state);
            case ExpirePaymentAuthorization expire -> expireAuthorization(expire, state);
        };
    }

    private PaymentEvent[] requestAuthorization(AuthorizePayment command, Payment state) {
        if (state.exists()) return noEvents;

        var requestedAt = now.get();

        return new PaymentEvent[] {
            new PaymentAuthorizationRequested(
                    command.paymentId(),
                    command.referenceId(),
                    command.amount(),
                    requestedAt,
                    paymentValidity.timesOutAt(requestedAt))
        };
    }

    private PaymentEvent[] recordCheckoutSession(RecordCheckoutSession command, Payment state) {
        var recordedAt = now.get();

        if (state.status() != Status.Pending
                || state.openCheckoutSession(recordedAt).isPresent()) return noEvents;

        return new PaymentEvent[] {
            new CheckoutSessionCreated(
                    state.id(), command.session().url(), command.session().expiresAt())
        };
    }

    private PaymentEvent[] confirmAuthorization(ConfirmPaymentAuthorization command, Payment state) {
        if (state.status() != Status.Pending) return noEvents;

        return new PaymentEvent[] {new PaymentAuthorized(state.id(), now.get(), command.captureBefore())};
    }

    private PaymentEvent[] requestCapture(Payment state) {
        if (state.status() != Status.Authorized) return noEvents;

        return new PaymentEvent[] {new PaymentCaptureRequested(state.id(), state.amount(), now.get())};
    }

    private PaymentEvent[] confirmCapture(Payment state) {
        if (state.status() != Status.CaptureRequested) return noEvents;

        return new PaymentEvent[] {new PaymentCaptured(state.id(), state.amount(), now.get())};
    }

    private PaymentEvent[] failCapture(FailPaymentCapture command, Payment state) {
        if (state.status() != Status.CaptureRequested) return noEvents;

        return new PaymentEvent[] {new PaymentCaptureFailed(state.id(), command.reason(), now.get())};
    }

    private PaymentEvent[] cancel(Payment state) {
        if (state.status() != Status.Authorized) return noEvents;

        return new PaymentEvent[] {new PaymentCanceled(state.id(), now.get())};
    }

    private PaymentEvent[] timeOut(TimeOutPayment command, Payment state) {
        if (state.status() != Status.Pending || command.timedOutAt().isBefore(state.timesOutAt())) return noEvents;

        return new PaymentEvent[] {new PaymentTimedOut(state.id(), command.timedOutAt())};
    }

    private PaymentEvent[] expireAuthorization(ExpirePaymentAuthorization command, Payment state) {
        if (state.status() != Status.Authorized || command.expiredAt().isBefore(state.captureBefore())) return noEvents;

        return new PaymentEvent[] {new PaymentAuthorizationExpired(state.id(), command.expiredAt())};
    }
}
