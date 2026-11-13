package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderCommand {
  record PlaceOrder(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice) implements OrderCommand {
  }

  record RecordOrderPaymentAuthorization(
    OrderId orderId,
    PaymentId paymentId,
    OffsetDateTime authorizedAt) implements OrderCommand {
  }

  record RecordOrderStockReservation(
    OrderId orderId,
    ShipmentId shipmentId,
    OffsetDateTime reservedAt) implements OrderCommand {
  }

  record RecordOrderPackageSent(
    OrderId orderId,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt) implements OrderCommand {
  }

  record RecordOrderPaymentCapture(
    OrderId orderId,
    OffsetDateTime capturedAt) implements OrderCommand {
  }

  record RecordOrderPaymentFailure(
    OrderId orderId,
    OffsetDateTime failedAt) implements OrderCommand {
  }

  record RecordOrderShipmentFailure(
    OrderId orderId,
    OrderCancellationReason reason,
    OffsetDateTime failedAt) implements OrderCommand {
  }

  record RetryOrderPayment(
    OrderId orderId) implements OrderCommand {
  }

  record CloseUnpaidOrder(
    OrderId orderId) implements OrderCommand {
  }

  record CancelOrder(
    OrderId orderId,
    OrderCancellationReason cancellationReason) implements OrderCommand {
  }
}
