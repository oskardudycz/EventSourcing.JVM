package io.eventdriven.ecommerce.orders.getorderstatus;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;
import static io.eventdriven.ecommerce.orders.getorderstatus.OrderStatus.Payment;
import static io.eventdriven.ecommerce.orders.getorderstatus.OrderStatus.Shipment;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.orders.Order;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class OrderStatuses extends Projection<OrderStatus, Order.Id> {
    public OrderStatuses(OrderStatusRepository repository) {
        super(repository);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                OrderPlaced.class,
                OrderPaymentAuthorized.class,
                OrderStockReserved.class,
                OrderConfirmed.class,
                OrderPackageSent.class,
                OrderPaymentCaptured.class,
                OrderPaymentFailed.class,
                OrderShipmentFailed.class,
                OrderPaymentRetried.class,
                OrderCompleted.class,
                OrderCanceled.class,
                OrderClosedUnpaid.class);
    }

    @Override
    protected Order.Id getDocumentId(Object event) {
        return switch (event) {
            case OrderPlaced e -> e.orderId();
            case OrderPaymentAuthorized e -> e.orderId();
            case OrderStockReserved e -> e.orderId();
            case OrderConfirmed e -> e.orderId();
            case OrderPackageSent e -> e.orderId();
            case OrderPaymentCaptured e -> e.orderId();
            case OrderPaymentFailed e -> e.orderId();
            case OrderShipmentFailed e -> e.orderId();
            case OrderPaymentRetried e -> e.orderId();
            case OrderCompleted e -> e.orderId();
            case OrderCanceled e -> e.orderId();
            case OrderClosedUnpaid e -> e.orderId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable OrderStatus evolve(@Nullable OrderStatus current, Object event) {
        return switch (event) {
            case OrderPlaced e ->
                new OrderStatus(
                        e.orderId(),
                        e.customerId(),
                        e.productItems(),
                        e.totalPrice(),
                        e.placedAt(),
                        io.eventdriven.ecommerce.payments.Payment.Id.derivedFrom(
                                e.orderId().value()));
            case OrderPaymentAuthorized e -> current.withPayment(Payment.Authorized, e.paymentId());
            case OrderStockReserved _ -> current.withShipment(Shipment.Reserved);
            case OrderConfirmed _ -> current.confirmed();
            case OrderPackageSent e -> current.sent(e.carrier(), e.trackingNumber());
            case OrderPaymentCaptured _ -> current.withPayment(Payment.Captured);
            case OrderPaymentFailed _ -> current.withPayment(Payment.Failed);
            case OrderShipmentFailed _ -> current.withShipment(Shipment.Failed);
            case OrderPaymentRetried e -> current.withPayment(Payment.Pending, e.paymentId());
            case OrderCompleted _ -> current.ended(Order.Progress.Completed);
            case OrderCanceled _ -> current.ended(Order.Progress.Canceled);
            case OrderClosedUnpaid _ -> current.ended(Order.Progress.ClosedUnpaid);
            default -> current;
        };
    }
}
