package io.eventdriven.ecommerce.orders.getcustomerorders;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.orders.Order;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class CustomerOrders extends Projection<CustomerOrder, Order.Id> {
    private final CustomerOrderRepository repository;

    public CustomerOrders(CustomerOrderRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                OrderPlaced.class,
                OrderPaymentAuthorized.class,
                OrderConfirmed.class,
                OrderPackageSent.class,
                OrderPaymentFailed.class,
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
            case OrderConfirmed e -> e.orderId();
            case OrderPackageSent e -> e.orderId();
            case OrderPaymentFailed e -> e.orderId();
            case OrderPaymentRetried e -> e.orderId();
            case OrderCompleted e -> e.orderId();
            case OrderCanceled e -> e.orderId();
            case OrderClosedUnpaid e -> e.orderId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable CustomerOrder evolve(@Nullable CustomerOrder current, Object event) {
        return switch (event) {
            case OrderPlaced e ->
                new CustomerOrder(
                        e.orderId(),
                        e.customerId(),
                        e.placedAt(),
                        e.totalPrice(),
                        Order.Progress.AwaitingPayment,
                        false);
            case OrderPaymentAuthorized _ ->
                current.with(
                        current.hasShipped() ? Order.Progress.Shipped : Order.Progress.Processing,
                        current.hasShipped());
            case OrderConfirmed _ -> current.with(Order.Progress.Processing, false);
            case OrderPackageSent _ -> current.with(Order.Progress.Shipped, true);
            case OrderPaymentFailed _ -> current.with(Order.Progress.PaymentFailed, current.hasShipped());
            case OrderPaymentRetried _ -> current.with(Order.Progress.AwaitingPayment, current.hasShipped());
            case OrderCompleted _ -> current.with(Order.Progress.Completed, true);
            case OrderCanceled _ -> current.with(Order.Progress.Canceled, current.hasShipped());
            case OrderClosedUnpaid _ -> current.with(Order.Progress.ClosedUnpaid, true);
            default -> current;
        };
    }

    public List<CustomerOrder> of(UUID customerId) {
        return repository.findByCustomerIdOrderByPlacedAtDesc(customerId);
    }
}
