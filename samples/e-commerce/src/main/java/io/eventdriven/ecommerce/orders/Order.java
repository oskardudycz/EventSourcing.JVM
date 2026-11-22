package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.core.identifiers.EntityId;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import jakarta.persistence.Embeddable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Order(
        Id id,
        double totalPrice,
        Status status,
        PaymentProgress payment,
        ShipmentProgress shipment,
        Payment.Id paymentId,
        Shipment.Id shipmentId,
        int paymentAttempts,
        OffsetDateTime dueAt,
        CancellationReason shipmentFailureReason) {

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
                        Payment.Id.derivedFrom(placed.orderId().value()),
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
                        failed.reason() == CancellationReason.CouldNotShip
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

    @Embeddable
    public record Id(@JsonValue String value) implements EntityId {
        private static final String type = "order";

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Id {
            Urns.requireType(value, type);
        }

        public static Id of(UUID id) {
            return new Id(Urns.of("ecommerce", type, id));
        }

        public static Id derivedFrom(String sourceUrn) {
            return new Id(Urns.derive(sourceUrn, type));
        }

        /** The customer's order number: the number of the cart that the order was placed from. */
        public String number() {
            return tail().substring(tail().lastIndexOf('-') + 1);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum PaymentStatus {
        NotAuthorized,
        Authorized
    }

    public enum ShipmentStatus {
        NotReserved,
        Reserved
    }

    public enum CancellationReason {
        ProductWasOutOfStock,
        CouldNotShip,
        PaymentFailed,
        Requested
    }

    public enum Progress {
        AwaitingPayment("Awaiting payment"),
        PaymentFailed("Payment failed"),
        OutOfStock("Out of stock"),
        Processing("Processing"),
        Shipped("Shipped"),
        Completed("Completed"),
        Canceled("Canceled"),
        ClosedUnpaid("Closed unpaid");

        private final String label;

        Progress(String label) {
            this.label = label;
        }

        public boolean isFinal() {
            return this == Completed || this == Canceled || this == ClosedUnpaid;
        }

        public String label() {
            return label;
        }
    }

    public record PricedProductItem(UUID productId, int quantity, double unitPrice) {}
}
