package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import java.time.OffsetDateTime;

public record Order(
        OrderId id,
        double totalPrice,
        Status status,
        PaymentProgress payment,
        ShipmentProgress shipment,
        PaymentId paymentId,
        ShipmentId shipmentId,
        int paymentAttempts,
        OffsetDateTime dueAt,
        OrderCancellationReason shipmentFailureReason) {

    public enum Status {
        Opened,
        Confirmed,
        Completed,
        ClosedUnpaid,
        Canceled
    }

    enum PaymentProgress {
        Pending,
        Authorized,
        Captured,
        Failed,
        Retrying
    }

    enum ShipmentProgress {
        Pending,
        Reserved,
        Sent,
        Rejected,
        Failed
    }

    public static Order initial() {
        return new Order(null, 0, null, null, null, null, null, 0, null, null);
    }

    boolean isOpen() {
        return status == Status.Opened || status == Status.Confirmed;
    }

    boolean hasShipped() {
        return shipment == ShipmentProgress.Sent;
    }

    boolean canStillBePaid() {
        return status == Status.Opened || status == Status.Confirmed && hasShipped();
    }

    public static Order evolve(Order state, OrderEvent event) {
        return switch (event) {
            case OrderPlaced placed ->
                new Order(
                        placed.orderId(),
                        placed.totalPrice(),
                        Status.Opened,
                        PaymentProgress.Pending,
                        ShipmentProgress.Pending,
                        PaymentId.derivedFrom(placed.orderId().value()),
                        state.shipmentId,
                        1,
                        state.dueAt,
                        state.shipmentFailureReason);
            case OrderPaymentAuthorized authorized ->
                new Order(
                        state.id,
                        state.totalPrice,
                        state.status,
                        PaymentProgress.Authorized,
                        state.shipment,
                        authorized.paymentId(),
                        state.shipmentId,
                        state.paymentAttempts,
                        state.dueAt,
                        state.shipmentFailureReason);
            case OrderStockReserved reserved ->
                new Order(
                        state.id,
                        state.totalPrice,
                        state.status,
                        state.payment,
                        ShipmentProgress.Reserved,
                        state.paymentId,
                        reserved.shipmentId(),
                        state.paymentAttempts,
                        state.dueAt,
                        state.shipmentFailureReason);
            case OrderConfirmed ignored -> state.withStatus(Status.Confirmed);
            case OrderPaymentCaptured ignored -> state.withPayment(PaymentProgress.Captured);
            case OrderPackageSent ignored -> state.withShipment(ShipmentProgress.Sent);
            case OrderPaymentDue ignored -> state;
            case OrderPaymentFailed failed ->
                new Order(
                        state.id,
                        state.totalPrice,
                        state.status,
                        PaymentProgress.Failed,
                        state.shipment,
                        state.paymentId,
                        state.shipmentId,
                        state.paymentAttempts,
                        failed.dueAt(),
                        state.shipmentFailureReason);
            case OrderShipmentFailed failed ->
                new Order(
                        state.id,
                        state.totalPrice,
                        state.status,
                        state.payment,
                        failed.reason() == OrderCancellationReason.CouldNotShip
                                ? ShipmentProgress.Rejected
                                : ShipmentProgress.Failed,
                        state.paymentId,
                        state.shipmentId,
                        state.paymentAttempts,
                        state.dueAt,
                        failed.reason());
            case OrderPaymentRetried retried ->
                new Order(
                        state.id,
                        state.totalPrice,
                        state.status,
                        PaymentProgress.Retrying,
                        state.shipment,
                        retried.paymentId(),
                        state.shipmentId,
                        state.paymentAttempts + 1,
                        state.dueAt,
                        state.shipmentFailureReason);
            case OrderClosedUnpaid ignored -> state.withStatus(Status.ClosedUnpaid);
            case OrderCompleted ignored -> state.withStatus(Status.Completed);
            case OrderCanceled ignored -> state.withStatus(Status.Canceled);
        };
    }

    private Order withStatus(Status status) {
        return new Order(
                id,
                totalPrice,
                status,
                payment,
                shipment,
                paymentId,
                shipmentId,
                paymentAttempts,
                dueAt,
                shipmentFailureReason);
    }

    private Order withPayment(PaymentProgress payment) {
        return new Order(
                id,
                totalPrice,
                status,
                payment,
                shipment,
                paymentId,
                shipmentId,
                paymentAttempts,
                dueAt,
                shipmentFailureReason);
    }

    private Order withShipment(ShipmentProgress shipment) {
        return new Order(
                id,
                totalPrice,
                status,
                payment,
                shipment,
                paymentId,
                shipmentId,
                paymentAttempts,
                dueAt,
                shipmentFailureReason);
    }
}
