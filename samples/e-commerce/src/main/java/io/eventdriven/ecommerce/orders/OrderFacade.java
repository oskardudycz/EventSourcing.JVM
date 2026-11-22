package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.ecommerce.orders.OrderDecider.*;

import io.eventdriven.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

public class OrderFacade {
    private final CommandHandler<Order.Id, Order, OrderEvent> commandHandler;
    private final PaymentTerms paymentTerms;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(Order.Id orderId) {
        return "Order-%s".formatted(orderId.tail());
    }

    public OrderFacade(
            CommandHandler<Order.Id, Order, OrderEvent> commandHandler,
            PaymentTerms paymentTerms,
            Supplier<OffsetDateTime> now) {
        this.commandHandler = commandHandler;
        this.paymentTerms = paymentTerms;
        this.now = now;
    }

    @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal, description = "Places the order."))
    public void placeOrder(PlaceOrder command) {
        var decided = new PlaceOrder(
                command.orderId(),
                command.cartId(),
                command.customerId(),
                command.productItems(),
                command.totalPrice(),
                now.get());

        commandHandler.handle(command.orderId(), decided, OrderDecider::place);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment is authorized. If the products are set aside too, the order is confirmed."))
    public void recordOrderPaymentAuthorization(RecordOrderPaymentAuthorization command) {
        commandHandler.handle(command.orderId(), command, OrderDecider::recordPaymentAuthorization);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products are set aside. If the payment is authorized too, the order is confirmed."))
    public void recordOrderStockReservation(RecordOrderStockReservation command) {
        commandHandler.handle(command.orderId(), command, OrderDecider::recordStockReservation);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Records that the package was sent. Then the shop charges the customer."))
    public void recordOrderPackageSent(RecordOrderPackageSent command) {
        commandHandler.handle(command.orderId(), command, OrderDecider::recordPackageSent);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the customer was charged. If the package was sent, the order is completed."))
    public void recordOrderPaymentCapture(RecordOrderPaymentCapture command) {
        commandHandler.handle(command.orderId(), command, OrderDecider::recordPaymentCapture);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the payment failed. The order waits for the customer to pay again, until its due date."))
    public void recordOrderPaymentFailure(RecordOrderPaymentFailure command) {
        var decided = new RecordOrderPaymentFailure(
                command.orderId(), command.paymentId(), command.failedAt(), paymentTerms.dueAt(command.failedAt()));

        commandHandler.handle(command.orderId(), decided, OrderDecider::recordPaymentFailure);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description =
                                    "Records that the products cannot ship. The order is canceled when the payment has answered."))
    public void recordOrderShipmentFailure(RecordOrderShipmentFailure command) {
        commandHandler.handle(command.orderId(), command, OrderDecider::recordShipmentFailure);
    }

    public void retryOrderPayment(RetryOrderPayment command) {
        var decided = new RetryOrderPayment(command.orderId(), now.get());

        commandHandler.handle(command.orderId(), decided, OrderDecider::retryPayment);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = OrdersConfig.internal,
                            description = "Closes the order if it is still unpaid at its due date."))
    public void closeUnpaidOrder(CloseUnpaidOrder command) {
        var decided = new CloseUnpaidOrder(command.orderId(), now.get());

        commandHandler.handleContinuingTrace(command.orderId(), decided, OrderDecider::closeUnpaid);
    }

    public void cancelOrder(CancelOrder command) {
        var decided = new CancelOrder(command.orderId(), command.cancellationReason(), now.get());

        commandHandler.handle(command.orderId(), decided, OrderDecider::cancel);
    }

    public boolean isPlaced(Order.Id orderId) {
        return commandHandler.get(orderId).id() != null;
    }

    public interface PaymentTerms {
        OffsetDateTime dueAt(OffsetDateTime failedAt);

        static PaymentTerms within(Duration timeToPay) {
            return failedAt -> failedAt.plus(timeToPay);
        }
    }
}
