package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.ecommerce.payments.Payment.Status;

public final class PaymentDecider {
    private static final PaymentEvent[] noEvents = new PaymentEvent[0];

    private PaymentDecider() {}

    public static PaymentEvent[] decide(PaymentCommand command, Payment state) {
        return switch (command) {
            case AuthorizePayment authorize -> requestAuthorization(authorize, state);
            case RecordCheckoutSession record -> recordCheckoutSession(record, state);
            case ConfirmPaymentAuthorization confirm -> confirmAuthorization(confirm, state);
            case CapturePayment capture -> requestCapture(capture, state);
            case ConfirmPaymentCapture confirm -> confirmCapture(confirm, state);
            case FailPaymentCapture fail -> failCapture(fail, state);
            case CancelPayment cancel -> cancel(cancel, state);
            case TimeOutPayment timeOut -> timeOut(timeOut, state);
            case ExpirePaymentAuthorization expire -> expireAuthorization(expire, state);
        };
    }

    public static PaymentEvent[] requestAuthorization(AuthorizePayment command, Payment state) {
        if (state.exists()) return noEvents;

        var requestedAt = command.now();

        return new PaymentEvent[] {
            new PaymentAuthorizationRequested(
                    command.paymentId(), command.referenceId(), command.amount(), requestedAt, command.timesOutAt())
        };
    }

    public static PaymentEvent[] recordCheckoutSession(RecordCheckoutSession command, Payment state) {
        var recordedAt = command.now();

        if (state.status() != Status.Pending
                || state.openCheckoutSession(recordedAt).isPresent()) return noEvents;

        return new PaymentEvent[] {
            new CheckoutSessionCreated(
                    state.id(), command.session().url(), command.session().expiresAt())
        };
    }

    public static PaymentEvent[] confirmAuthorization(ConfirmPaymentAuthorization command, Payment state) {
        if (state.status() != Status.Pending) return noEvents;

        return new PaymentEvent[] {new PaymentAuthorized(state.id(), command.now(), command.captureBefore())};
    }

    public static PaymentEvent[] requestCapture(CapturePayment command, Payment state) {
        if (state.status() != Status.Authorized) return noEvents;

        return new PaymentEvent[] {new PaymentCaptureRequested(state.id(), state.amount(), command.now())};
    }

    public static PaymentEvent[] confirmCapture(ConfirmPaymentCapture command, Payment state) {
        if (state.status() != Status.CaptureRequested) return noEvents;

        return new PaymentEvent[] {new PaymentCaptured(state.id(), state.amount(), command.now())};
    }

    public static PaymentEvent[] failCapture(FailPaymentCapture command, Payment state) {
        if (state.status() != Status.CaptureRequested) return noEvents;

        return new PaymentEvent[] {new PaymentCaptureFailed(state.id(), command.reason(), command.now())};
    }

    public static PaymentEvent[] cancel(CancelPayment command, Payment state) {
        if (state.status() != Status.Authorized) return noEvents;

        return new PaymentEvent[] {new PaymentCanceled(state.id(), command.now())};
    }

    public static PaymentEvent[] timeOut(TimeOutPayment command, Payment state) {
        if (state.status() != Status.Pending || command.timedOutAt().isBefore(state.timesOutAt())) return noEvents;

        return new PaymentEvent[] {new PaymentTimedOut(state.id(), command.timedOutAt())};
    }

    public static PaymentEvent[] expireAuthorization(ExpirePaymentAuthorization command, Payment state) {
        if (state.status() != Status.Authorized || command.expiredAt().isBefore(state.captureBefore())) return noEvents;

        return new PaymentEvent[] {new PaymentAuthorizationExpired(state.id(), command.expiredAt())};
    }
}
