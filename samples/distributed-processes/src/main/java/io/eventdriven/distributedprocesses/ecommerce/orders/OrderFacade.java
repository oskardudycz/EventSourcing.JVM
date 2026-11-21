package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;

public class OrderFacade {
    private final CommandHandler<OrderId, Order, OrderCommand, OrderEvent> commandHandler;

    public static String mapToStreamId(OrderId orderId) {
        return "Order-%s".formatted(orderId.tail());
    }

    public OrderFacade(CommandHandler<OrderId, Order, OrderCommand, OrderEvent> commandHandler) {
        this.commandHandler = commandHandler;
    }

    @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal, description = "Places the order."))
    public void placeOrder(PlaceOrder command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment is authorized. If the products are set aside too, the order is confirmed."))
    public void recordOrderPaymentAuthorization(RecordOrderPaymentAuthorization command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products are set aside. If the payment is authorized too, the order is confirmed."))
    public void recordOrderStockReservation(RecordOrderStockReservation command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Records that the package was sent. Then the shop charges the customer."))
    public void recordOrderPackageSent(RecordOrderPackageSent command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the customer was charged. If the package was sent, the order is completed."))
    public void recordOrderPaymentCapture(RecordOrderPaymentCapture command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment failed. The order waits for the customer to pay again, until its due date."))
    public void recordOrderPaymentFailure(RecordOrderPaymentFailure command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products cannot ship. The order is canceled when the payment has answered."))
    public void recordOrderShipmentFailure(RecordOrderShipmentFailure command) {
        commandHandler.handle(command.orderId(), command);
    }

    public void retryOrderPayment(RetryOrderPayment command) {
        commandHandler.handle(command.orderId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Closes the order if it is still unpaid at its due date."))
    public void closeUnpaidOrder(CloseUnpaidOrder command) {
        commandHandler.handleContinuingTrace(command.orderId(), command);
    }

    public void cancelOrder(CancelOrder command) {
        commandHandler.handle(command.orderId(), command);
    }
}
