package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;

public final class ECommerceMessages {
    private ECommerceMessages() {}

    public static MessageMapping mapping() {
        return new MessageMapping()
                .register("payments.AuthorizePayment", PaymentCommand.AuthorizePayment.class)
                .register("payments.CreateCheckoutSession", PaymentCommand.CreateCheckoutSession.class)
                .register("payments.ConfirmPaymentAuthorization", PaymentCommand.ConfirmPaymentAuthorization.class)
                .register("payments.CapturePayment", PaymentCommand.CapturePayment.class)
                .register("payments.ConfirmPaymentCapture", PaymentCommand.ConfirmPaymentCapture.class)
                .register("payments.FailPaymentCapture", PaymentCommand.FailPaymentCapture.class)
                .register("payments.CancelPayment", PaymentCommand.CancelPayment.class)
                .register("payments.TimeOutPayment", PaymentCommand.TimeOutPayment.class)
                .register("payments.ExpirePaymentAuthorization", PaymentCommand.ExpirePaymentAuthorization.class)
                .register("payments.PaymentAuthorized", PaymentExternalEvent.PaymentAuthorized.class)
                .register("payments.PaymentCaptured", PaymentExternalEvent.PaymentCaptured.class)
                .register("payments.PaymentFailed", PaymentExternalEvent.PaymentFailed.class)
                .register("shipments.ReserveStock", ShipmentCommand.ReserveStock.class)
                .register("shipments.RequestFulfillment", ShipmentCommand.RequestFulfillment.class)
                .register("shipments.ConfirmPackageSent", ShipmentCommand.ConfirmPackageSent.class)
                .register("shipments.RejectFulfillment", ShipmentCommand.RejectFulfillment.class)
                .register("shipments.DeliverPackage", ShipmentCommand.DeliverPackage.class)
                .register("shipments.ReleaseStock", ShipmentCommand.ReleaseStock.class)
                .register("shipments.StockReserved", ShipmentExternalEvent.StockReserved.class)
                .register("shipments.PackageWasSent", ShipmentExternalEvent.PackageWasSent.class)
                .register("shipments.ShipmentFailed", ShipmentExternalEvent.ShipmentFailed.class)
                .register("orders.PlaceOrder", OrderCommand.PlaceOrder.class)
                .register("orders.RecordOrderPaymentAuthorization", OrderCommand.RecordOrderPaymentAuthorization.class)
                .register("orders.RecordOrderStockReservation", OrderCommand.RecordOrderStockReservation.class)
                .register("orders.RecordOrderPackageSent", OrderCommand.RecordOrderPackageSent.class)
                .register("orders.RecordOrderPaymentCapture", OrderCommand.RecordOrderPaymentCapture.class)
                .register("orders.RecordOrderPaymentFailure", OrderCommand.RecordOrderPaymentFailure.class)
                .register("orders.RecordOrderShipmentFailure", OrderCommand.RecordOrderShipmentFailure.class)
                .register("orders.RetryOrderPayment", OrderCommand.RetryOrderPayment.class)
                .register("orders.CloseUnpaidOrder", OrderCommand.CloseUnpaidOrder.class)
                .register("orders.CancelOrder", OrderCommand.CancelOrder.class)
                .register(
                        "shopping_carts.ShoppingCartCheckedOut",
                        ShoppingCartExternalEvent.ShoppingCartCheckedOut.class);
    }
}
