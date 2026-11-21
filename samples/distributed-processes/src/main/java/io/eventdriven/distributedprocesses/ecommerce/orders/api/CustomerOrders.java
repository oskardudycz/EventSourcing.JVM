package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class CustomerOrders extends Projection<CustomerOrder, OrderId> {
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
    protected OrderId getDocumentId(Object event) {
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
                        OrderProgress.AwaitingPayment,
                        false);
            case OrderPaymentAuthorized _ ->
                current.with(
                        current.hasShipped() ? OrderProgress.Shipped : OrderProgress.Processing, current.hasShipped());
            case OrderConfirmed _ -> current.with(OrderProgress.Processing, false);
            case OrderPackageSent _ -> current.with(OrderProgress.Shipped, true);
            case OrderPaymentFailed _ -> current.with(OrderProgress.PaymentFailed, current.hasShipped());
            case OrderPaymentRetried _ -> current.with(OrderProgress.AwaitingPayment, current.hasShipped());
            case OrderCompleted _ -> current.with(OrderProgress.Completed, true);
            case OrderCanceled _ -> current.with(OrderProgress.Canceled, current.hasShipped());
            case OrderClosedUnpaid _ -> current.with(OrderProgress.ClosedUnpaid, true);
            default -> current;
        };
    }

    public List<CustomerOrder> of(UUID customerId) {
        return repository.findByCustomerIdOrderByPlacedAtDesc(customerId);
    }
}
