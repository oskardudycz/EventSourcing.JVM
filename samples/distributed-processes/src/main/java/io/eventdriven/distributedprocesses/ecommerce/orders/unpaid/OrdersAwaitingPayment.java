package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

// Orders whose payment failed, kept until they are paid, closed or canceled. The order decides
// whether a wake-up at the due date ends it.
public class OrdersAwaitingPayment extends Projection<OrderAwaitingPayment, OrderId> {
    private final OrderAwaitingPaymentRepository repository;

    public OrdersAwaitingPayment(OrderAwaitingPaymentRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(OrderPaymentFailed.class, OrderCompleted.class, OrderClosedUnpaid.class, OrderCanceled.class);
    }

    @Override
    protected OrderId getDocumentId(Object event) {
        return switch (event) {
            case OrderPaymentFailed e -> e.orderId();
            case OrderCompleted e -> e.orderId();
            case OrderClosedUnpaid e -> e.orderId();
            case OrderCanceled e -> e.orderId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable OrderAwaitingPayment evolve(@Nullable OrderAwaitingPayment current, Object event) {
        return switch (event) {
            case OrderPaymentFailed e -> new OrderAwaitingPayment(e.orderId(), e.dueAt());
            case OrderCompleted _, OrderClosedUnpaid _, OrderCanceled _ -> null;
            default -> current;
        };
    }

    public List<OrderId> overdueAt(OffsetDateTime now) {
        return repository.findByDueAtLessThanEqual(now).stream()
                .map(OrderAwaitingPayment::getOrderId)
                .toList();
    }
}
