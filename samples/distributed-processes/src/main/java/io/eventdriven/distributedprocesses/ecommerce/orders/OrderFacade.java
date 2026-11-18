package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

public class OrderFacade {
    private final AggregateStore<Order, OrderEvent, OrderId> store;
    private final PaymentTerms paymentTerms;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(OrderId orderId) {
        return "Order-%s".formatted(orderId.tail());
    }

    public OrderFacade(
            AggregateStore<Order, OrderEvent, OrderId> store, PaymentTerms paymentTerms, Supplier<OffsetDateTime> now) {
        this.store = store;
        this.paymentTerms = paymentTerms;
        this.now = now;
    }

    @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal, description = "Places the order."))
    public void placeOrder(PlaceOrder command) {
        store.getAndUpdate(
                command.orderId(),
                current -> current.place(
                        command.orderId(),
                        command.cartId(),
                        command.customerId(),
                        command.productItems(),
                        command.totalPrice(),
                        now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment is authorized. If the products are set aside too, the order is confirmed."))
    public void recordOrderPaymentAuthorization(RecordOrderPaymentAuthorization command) {
        store.getAndUpdate(
                command.orderId(),
                current -> current.recordPaymentAuthorization(command.paymentId(), command.authorizedAt()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products are set aside. If the payment is authorized too, the order is confirmed."))
    public void recordOrderStockReservation(RecordOrderStockReservation command) {
        store.getAndUpdate(
                command.orderId(),
                current -> current.recordStockReservation(command.shipmentId(), command.reservedAt()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Records that the package was sent. Then the shop charges the customer."))
    public void recordOrderPackageSent(RecordOrderPackageSent command) {
        store.getAndUpdate(
                command.orderId(),
                current -> current.recordPackageSent(command.carrier(), command.trackingNumber(), command.sentAt()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the customer was charged. If the package was sent, the order is completed."))
    public void recordOrderPaymentCapture(RecordOrderPaymentCapture command) {
        store.getAndUpdate(command.orderId(), current -> current.recordPaymentCapture(command.capturedAt()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment failed. The order waits for the customer to pay again, until its due date."))
    public void recordOrderPaymentFailure(RecordOrderPaymentFailure command) {
        store.getAndUpdate(
                command.orderId(),
                current -> current.recordPaymentFailure(command.failedAt(), paymentTerms.dueAt(command.failedAt())));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products cannot ship. The order is canceled when the payment has answered."))
    public void recordOrderShipmentFailure(RecordOrderShipmentFailure command) {
        store.getAndUpdate(
                command.orderId(), current -> current.recordShipmentFailure(command.reason(), command.failedAt()));
    }

    public void retryOrderPayment(RetryOrderPayment command) {
        store.getAndUpdate(command.orderId(), current -> current.retryPayment(now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Closes the order if it is still unpaid at its due date."))
    public void closeUnpaidOrder(CloseUnpaidOrder command) {
        store.getAndUpdate(command.orderId(), current -> current.closeUnpaid(now.get()));
    }

    public void cancelOrder(CancelOrder command) {
        store.getAndUpdate(command.orderId(), current -> current.cancel(command.cancellationReason(), now.get()));
    }
}
