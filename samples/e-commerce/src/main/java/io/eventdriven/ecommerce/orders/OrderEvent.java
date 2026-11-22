package io.eventdriven.ecommerce.orders;

import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderEvent {
    record OrderPlaced(
            Order.Id orderId,
            ShoppingCart.Id cartId,
            UUID customerId,
            Order.PricedProductItem[] productItems,
            double totalPrice,
            OffsetDateTime placedAt)
            implements OrderEvent {}

    record OrderPaymentAuthorized(Order.Id orderId, Payment.Id paymentId, OffsetDateTime authorizedAt)
            implements OrderEvent {}

    record OrderStockReserved(Order.Id orderId, Shipment.Id shipmentId, OffsetDateTime reservedAt)
            implements OrderEvent {}

    record OrderConfirmed(Order.Id orderId, Payment.Id paymentId, Shipment.Id shipmentId, OffsetDateTime confirmedAt)
            implements OrderEvent {}

    record OrderPaymentCaptured(Order.Id orderId, Payment.Id paymentId, double amount, OffsetDateTime capturedAt)
            implements OrderEvent {}

    record OrderPackageSent(Order.Id orderId, String carrier, String trackingNumber, OffsetDateTime sentAt)
            implements OrderEvent {}

    record OrderPaymentDue(Order.Id orderId, Payment.Id paymentId, OffsetDateTime dueAt) implements OrderEvent {}

    record OrderPaymentFailed(Order.Id orderId, OffsetDateTime failedAt, OffsetDateTime dueAt) implements OrderEvent {}

    record OrderShipmentFailed(Order.Id orderId, Order.CancellationReason reason, OffsetDateTime failedAt)
            implements OrderEvent {}

    record OrderPaymentRetried(Order.Id orderId, Payment.Id paymentId, double amount, OffsetDateTime retriedAt)
            implements OrderEvent {}

    record OrderClosedUnpaid(Order.Id orderId, OffsetDateTime closedAt) implements OrderEvent {}

    record OrderCompleted(Order.Id orderId, OffsetDateTime completedAt) implements OrderEvent {}

    record OrderCanceled(
            Order.Id orderId,
            Payment.Id paymentId, // the order's current payment, authorized or not
            Order.PaymentStatus paymentStatus,
            Shipment.Id shipmentId, // nullable — an order canceled before the reservation has none
            Order.ShipmentStatus shipmentStatus,
            Order.CancellationReason reason,
            OffsetDateTime canceledAt)
            implements OrderEvent {}
}
