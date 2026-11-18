package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import java.util.List;
import org.jspecify.annotations.Nullable;

// Orders that are still running: a to-do list for whoever has to chase them.
public class PendingOrders extends Projection<PendingOrder> {
    public PendingOrders(Database database) {
        super(PendingOrder.class, database);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(OrderPlaced.class, OrderCompleted.class, OrderClosedUnpaid.class, OrderCanceled.class);
    }

    @Override
    protected EntityId getDocumentId(Object event) {
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

    public List<PendingOrder> all() {
        return find(pending -> true);
    }
}
