package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderCommand {
    OrderId orderId();

    @Schema(
            description =
                    "Places an order for the products of a checked-out shopping cart, at the prices of the checkout.")
    record PlaceOrder(
            OrderId orderId,
            ShoppingCartId cartId,
            UUID customerId,
            PricedProductItem[] productItems,
            double totalPrice)
            implements OrderCommand {}

    @Schema(description = "Records that the order's payment is authorized. The customer is not charged yet.")
    record RecordOrderPaymentAuthorization(OrderId orderId, PaymentId paymentId, OffsetDateTime authorizedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the warehouse set aside the order's products.")
    record RecordOrderStockReservation(OrderId orderId, ShipmentId shipmentId, OffsetDateTime reservedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the package left the warehouse, with the carrier and the tracking number.")
    record RecordOrderPackageSent(OrderId orderId, String carrier, String trackingNumber, OffsetDateTime sentAt)
            implements OrderCommand {}

    @Schema(description = "Records that the customer was charged for the order.")
    record RecordOrderPaymentCapture(OrderId orderId, PaymentId paymentId, OffsetDateTime capturedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the order's payment failed.")
    record RecordOrderPaymentFailure(OrderId orderId, PaymentId paymentId, OffsetDateTime failedAt)
            implements OrderCommand {}

    @Schema(description = "Records that the order's products cannot ship, and why.")
    record RecordOrderShipmentFailure(OrderId orderId, OrderCancellationReason reason, OffsetDateTime failedAt)
            implements OrderCommand {}

    @Schema(description = "Starts a new payment for an order whose payment failed.")
    record RetryOrderPayment(OrderId orderId) implements OrderCommand {}

    @Schema(
            description =
                    "Closes an order that is still unpaid at its due date. An order that was not sent is canceled. An order that was sent is closed as unpaid.")
    record CloseUnpaidOrder(OrderId orderId) implements OrderCommand {}

    @Schema(description = "Cancels an order that is not confirmed yet.")
    record CancelOrder(OrderId orderId, OrderCancellationReason cancellationReason) implements OrderCommand {}
}
