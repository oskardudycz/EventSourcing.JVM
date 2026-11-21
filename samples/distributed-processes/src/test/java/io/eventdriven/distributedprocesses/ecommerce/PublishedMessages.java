package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.CaptureFailureReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.CheckoutSession;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PublishedMessages {
    private PublishedMessages() {}

    public static List<Object> of(
            OrderId orderId,
            PaymentId paymentId,
            ShipmentId shipmentId,
            ShoppingCartId cartId,
            UUID customerId,
            OffsetDateTime now) {
        var orderItems = new PricedProductItem[0];
        var cartItems =
                new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem[0];

        return List.of(
                new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), 10),
                new PaymentCommand.RecordCheckoutSession(
                        paymentId, new CheckoutSession(URI.create("http://localhost/checkout"), now)),
                new PaymentCommand.ConfirmPaymentAuthorization(paymentId, now),
                new PaymentCommand.CapturePayment(paymentId),
                new PaymentCommand.ConfirmPaymentCapture(paymentId),
                new PaymentCommand.FailPaymentCapture(paymentId, CaptureFailureReason.UnexpectedError),
                new PaymentCommand.CancelPayment(paymentId),
                new PaymentCommand.TimeOutPayment(paymentId, now),
                new PaymentCommand.ExpirePaymentAuthorization(paymentId, now),
                new ShipmentCommand.ReserveStock(orderId.value(), new ProductItem[0]),
                new ShipmentCommand.RequestFulfillment(shipmentId),
                new ShipmentCommand.ConfirmPackageSent(shipmentId, "DHL", "JD01"),
                new ShipmentCommand.RejectFulfillment(shipmentId),
                new ShipmentCommand.DeliverPackage(shipmentId),
                new ShipmentCommand.ReleaseStock(shipmentId),
                new OrderCommand.PlaceOrder(orderId, cartId, customerId, orderItems, 10),
                new OrderCommand.RecordOrderPaymentAuthorization(orderId, paymentId, now),
                new OrderCommand.RecordOrderStockReservation(orderId, shipmentId, now),
                new OrderCommand.RecordOrderPackageSent(orderId, "DHL", "JD01", now),
                new OrderCommand.RecordOrderPaymentCapture(orderId, paymentId, now),
                new OrderCommand.RecordOrderPaymentFailure(orderId, paymentId, now),
                new OrderCommand.RecordOrderShipmentFailure(orderId, OrderCancellationReason.Requested, now),
                new OrderCommand.RetryOrderPayment(orderId),
                new OrderCommand.CloseUnpaidOrder(orderId),
                new OrderCommand.CancelOrder(orderId, OrderCancellationReason.Requested),
                new PaymentExternalEvent.PaymentAuthorized(orderId.value(), paymentId, now),
                new PaymentExternalEvent.PaymentCaptured(orderId.value(), paymentId, now),
                new PaymentExternalEvent.PaymentFailed(orderId.value(), paymentId, now),
                new ShipmentExternalEvent.StockReserved(shipmentId, orderId.value(), now),
                new ShipmentExternalEvent.PackageWasSent(shipmentId, orderId.value(), "DHL", "JD01", now),
                new ShipmentExternalEvent.ShipmentFailed(
                        shipmentId,
                        orderId.value(),
                        ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock,
                        now),
                new ShoppingCartExternalEvent.ShoppingCartCheckedOut(cartId, customerId, cartItems, 10, now));
    }
}
