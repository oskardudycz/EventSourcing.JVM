package io.eventdriven.ecommerce.orders;

import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public sealed interface OrderCommand {
    Order.Id orderId();

    @Schema(
            description =
                    "Places an order for the products of a checked-out shopping cart, at the prices of the checkout.")
    record PlaceOrder(
            Order.Id orderId,
            ShoppingCart.Id cartId,
            UUID customerId,
            Order.PricedProductItem[] productItems,
            double totalPrice,
            @Nullable OffsetDateTime now)
            implements OrderCommand {
        public PlaceOrder(
                Order.Id orderId,
                ShoppingCart.Id cartId,
                UUID customerId,
                Order.PricedProductItem[] productItems,
                double totalPrice) {
            this(orderId, cartId, customerId, productItems, totalPrice, null);
        }
    }

    @Schema(description = "Records that the order's payment is authorized. The customer is not charged yet.")
    record RecordOrderPaymentAuthorization(Order.Id orderId, Payment.Id paymentId, OffsetDateTime authorizedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the warehouse set aside the order's products.")
    record RecordOrderStockReservation(Order.Id orderId, Shipment.Id shipmentId, OffsetDateTime reservedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the package left the warehouse, with the carrier and the tracking number.")
    record RecordOrderPackageSent(Order.Id orderId, String carrier, String trackingNumber, OffsetDateTime sentAt)
            implements OrderCommand {}

    @Schema(description = "Records that the customer was charged for the order.")
    record RecordOrderPaymentCapture(Order.Id orderId, Payment.Id paymentId, OffsetDateTime capturedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the order's payment failed.")
    record RecordOrderPaymentFailure(
            Order.Id orderId,
            Payment.Id paymentId,
            OffsetDateTime failedAt,
            @Nullable OffsetDateTime dueAt) implements OrderCommand {
        public RecordOrderPaymentFailure(Order.Id orderId, Payment.Id paymentId, OffsetDateTime failedAt) {
            this(orderId, paymentId, failedAt, null);
        }
    }

    @Schema(description = "Records that the order's products cannot ship, and why.")
    record RecordOrderShipmentFailure(Order.Id orderId, Order.CancellationReason reason, OffsetDateTime failedAt)
            implements OrderCommand {}

    @Schema(description = "Starts a new payment for an order whose payment failed.")
    record RetryOrderPayment(Order.Id orderId, @Nullable OffsetDateTime now) implements OrderCommand {
        public RetryOrderPayment(Order.Id orderId) {
            this(orderId, null);
        }
    }

    @Schema(
            description =
                    "Closes an order that is still unpaid at its due date. An order that was not sent is canceled. An order that was sent is closed as unpaid.")
    record CloseUnpaidOrder(Order.Id orderId, @Nullable OffsetDateTime now) implements OrderCommand {
        public CloseUnpaidOrder(Order.Id orderId) {
            this(orderId, null);
        }
    }

    @Schema(description = "Cancels an order that is not confirmed yet.")
    record CancelOrder(
            Order.Id orderId,
            Order.CancellationReason cancellationReason,
            @Nullable OffsetDateTime now) implements OrderCommand {
        public CancelOrder(Order.Id orderId, Order.CancellationReason cancellationReason) {
            this(orderId, cancellationReason, null);
        }
    }
}
