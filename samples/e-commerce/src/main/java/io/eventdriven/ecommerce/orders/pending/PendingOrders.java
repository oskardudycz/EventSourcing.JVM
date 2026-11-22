package io.eventdriven.ecommerce.orders.pending;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.orders.Order;
import java.util.List;
import org.jspecify.annotations.Nullable;

// Orders that are still running: a to-do list for whoever has to chase them.
public class PendingOrders extends Projection<PendingOrder, Order.Id> {
    private final PendingOrderRepository repository;

    public PendingOrders(PendingOrderRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(OrderPlaced.class, OrderCompleted.class, OrderClosedUnpaid.class, OrderCanceled.class);
    }

    @Override
    protected Order.Id getDocumentId(Object event) {
        return switch (event) {
            case OrderPlaced e -> e.orderId();
            case OrderCompleted e -> e.orderId();
            case OrderClosedUnpaid e -> e.orderId();
            case OrderCanceled e -> e.orderId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable PendingOrder evolve(@Nullable PendingOrder current, Object event) {
        return switch (event) {
            case OrderPlaced e -> new PendingOrder(e.orderId());
            case OrderCompleted _, OrderClosedUnpaid _, OrderCanceled _ -> null;
            default -> current;
        };
    }

    public long count() {
        return repository.count();
    }

    public List<PendingOrder> all() {
        return repository.findAll();
    }
}
