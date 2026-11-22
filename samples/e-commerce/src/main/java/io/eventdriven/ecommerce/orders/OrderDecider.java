package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.ecommerce.orders.Order.PaymentProgress;
import io.eventdriven.ecommerce.orders.Order.ShipmentProgress;
import io.eventdriven.ecommerce.orders.Order.Status;
import io.eventdriven.ecommerce.payments.Payment;
import java.time.OffsetDateTime;
import java.util.Optional;

public final class OrderDecider {
    private static final OrderEvent[] noEvents = new OrderEvent[0];

    private OrderDecider() {}

    public static OrderEvent[] decide(OrderCommand command, Order state) {
        return switch (command) {
            case PlaceOrder place -> place(place, state);
            case RecordOrderPaymentAuthorization authorization -> recordPaymentAuthorization(authorization, state);
            case RecordOrderStockReservation reservation -> recordStockReservation(reservation, state);
            case RecordOrderPackageSent packageSent -> recordPackageSent(packageSent, state);
            case RecordOrderPaymentCapture capture -> recordPaymentCapture(capture, state);
            case RecordOrderPaymentFailure failure -> recordPaymentFailure(failure, state);
            case RecordOrderShipmentFailure failure -> recordShipmentFailure(failure, state);
            case RetryOrderPayment retry -> retryPayment(retry, state);
            case CloseUnpaidOrder close -> closeUnpaid(close, state);
            case CancelOrder cancel -> cancel(cancel, state);
        };
    }

    public static OrderEvent[] place(PlaceOrder command, Order state) {
        if (state.status() != null) return noEvents;

        return new OrderEvent[] {
            new OrderPlaced(
                    command.orderId(),
                    command.cartId(),
                    command.customerId(),
                    command.productItems(),
                    command.totalPrice(),
                    command.now())
        };
    }

    public static OrderEvent[] recordPaymentAuthorization(RecordOrderPaymentAuthorization command, Order state) {
        var authorized = new OrderPaymentAuthorized(state.id(), command.paymentId(), command.authorizedAt());

        if (state.status() == Status.Opened && state.payment() == PaymentProgress.Pending)
            return withProgress(state, command.authorizedAt(), authorized);

        if (state.payment() != PaymentProgress.Retrying || !command.paymentId().equals(state.paymentId()))
            return noEvents;

        if (state.status() == Status.Opened) return withProgress(state, command.authorizedAt(), authorized);

        if (state.status() == Status.Confirmed)
            return new OrderEvent[] {
                authorized, new OrderPaymentDue(state.id(), command.paymentId(), command.authorizedAt())
            };

        return noEvents;
    }

    public static OrderEvent[] recordStockReservation(RecordOrderStockReservation command, Order state) {
        if (state.status() != Status.Opened || state.shipment() != ShipmentProgress.Pending) return noEvents;

        return withProgress(
                state,
                command.reservedAt(),
                new OrderStockReserved(state.id(), command.shipmentId(), command.reservedAt()));
    }

    public static OrderEvent[] recordPackageSent(RecordOrderPackageSent command, Order state) {
        if (state.status() != Status.Confirmed
                || state.payment() != PaymentProgress.Authorized
                || state.shipment() != ShipmentProgress.Reserved) return noEvents;

        return new OrderEvent[] {
            new OrderPackageSent(state.id(), command.carrier(), command.trackingNumber(), command.sentAt()),
            new OrderPaymentDue(state.id(), state.paymentId(), command.sentAt())
        };
    }

    public static OrderEvent[] recordPaymentCapture(RecordOrderPaymentCapture command, Order state) {
        if (state.status() != Status.Confirmed
                || state.payment() != PaymentProgress.Authorized
                || !state.hasShipped()
                || !command.paymentId().equals(state.paymentId())) return noEvents;

        return withProgress(
                state,
                command.capturedAt(),
                new OrderPaymentCaptured(state.id(), state.paymentId(), state.totalPrice(), command.capturedAt()));
    }

    public static OrderEvent[] recordPaymentFailure(RecordOrderPaymentFailure command, Order state) {
        if (!state.isOpen()
                || state.payment() == PaymentProgress.Captured
                || state.payment() == PaymentProgress.Failed
                || !command.paymentId().equals(state.paymentId())) return noEvents;

        var dueAt = state.dueAt() != null ? state.dueAt() : command.dueAt();

        return withProgress(state, command.failedAt(), new OrderPaymentFailed(state.id(), command.failedAt(), dueAt));
    }

    public static OrderEvent[] recordShipmentFailure(RecordOrderShipmentFailure command, Order state) {
        if (!state.isOpen()
                || state.shipment() != ShipmentProgress.Pending && state.shipment() != ShipmentProgress.Reserved)
            return noEvents;

        return withProgress(
                state, command.failedAt(), new OrderShipmentFailed(state.id(), command.reason(), command.failedAt()));
    }

    public static OrderEvent[] retryPayment(RetryOrderPayment command, Order state) {
        if (!state.canStillBePaid() || state.payment() != PaymentProgress.Failed) return noEvents;

        var nextPaymentId = Payment.Id.derivedFrom(state.id().value(), state.paymentAttempts() + 1);

        return new OrderEvent[] {new OrderPaymentRetried(state.id(), nextPaymentId, state.totalPrice(), command.now())};
    }

    public static OrderEvent[] closeUnpaid(CloseUnpaidOrder command, Order state) {
        var closedAt = command.now();

        if (state.payment() != PaymentProgress.Failed && state.payment() != PaymentProgress.Retrying
                || closedAt.isBefore(state.dueAt())) return noEvents;

        if (state.status() == Status.Opened)
            return new OrderEvent[] {canceled(state, Order.CancellationReason.PaymentFailed, closedAt)};

        if (state.status() == Status.Confirmed && state.hasShipped())
            return new OrderEvent[] {new OrderClosedUnpaid(state.id(), closedAt)};

        return noEvents;
    }

    public static OrderEvent[] cancel(CancelOrder command, Order state) {
        if (state.status() != Status.Opened) return noEvents;

        return new OrderEvent[] {canceled(state, command.cancellationReason(), command.now())};
    }

    private static OrderEvent[] withProgress(Order state, OffsetDateTime at, OrderEvent event) {
        return progress(Order.evolve(state, event), at)
                .map(next -> new OrderEvent[] {event, next})
                .orElseGet(() -> new OrderEvent[] {event});
    }

    private static Optional<OrderEvent> progress(Order state, OffsetDateTime at) {
        if (state.shipment() == ShipmentProgress.Failed || state.shipment() == ShipmentProgress.Rejected) {
            // The payment still has to say where it stands, because that is what decides
            // whether a hold must be canceled.
            if (state.payment() == PaymentProgress.Pending || state.payment() == PaymentProgress.Retrying)
                return Optional.empty();

            var reason = state.payment() == PaymentProgress.Failed
                    ? Order.CancellationReason.PaymentFailed
                    : state.shipmentFailureReason();

            return Optional.of(canceled(state, reason, at));
        }

        if (state.payment() == PaymentProgress.Failed) {
            // Before the confirmation the customer can still pay again, so the order stays open and
            // keeps its stock. Goods that already left cannot be held back, so the order stays open and
            // waits for the customer to pay again. Only a hold that lapses in between cancels.
            if (state.status() == Status.Confirmed && !state.hasShipped())
                return Optional.of(canceled(state, Order.CancellationReason.PaymentFailed, at));

            return Optional.empty();
        }

        if (state.status() == Status.Opened
                && state.payment() == PaymentProgress.Authorized
                && state.shipment() == ShipmentProgress.Reserved)
            return Optional.of(new OrderConfirmed(state.id(), state.paymentId(), state.shipmentId(), at));

        if (state.status() == Status.Confirmed
                && state.payment() == PaymentProgress.Captured
                && state.shipment() == ShipmentProgress.Sent) return Optional.of(new OrderCompleted(state.id(), at));

        return Optional.empty();
    }

    private static OrderCanceled canceled(Order state, Order.CancellationReason reason, OffsetDateTime at) {
        return new OrderCanceled(
                state.id(),
                state.paymentId(),
                paymentStatusOf(state),
                state.shipmentId(),
                shipmentStatusOf(state),
                reason,
                at);
    }

    private static Order.PaymentStatus paymentStatusOf(Order state) {
        return state.payment() == PaymentProgress.Authorized
                ? Order.PaymentStatus.Authorized
                : Order.PaymentStatus.NotAuthorized;
    }

    private static Order.ShipmentStatus shipmentStatusOf(Order state) {
        return state.shipment() == ShipmentProgress.Reserved || state.shipment() == ShipmentProgress.Rejected
                ? Order.ShipmentStatus.Reserved
                : Order.ShipmentStatus.NotReserved;
    }
}
