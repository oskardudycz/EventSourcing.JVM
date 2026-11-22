package io.eventdriven.ecommerce.orders.pending;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.orders.Order;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class PendingOrder implements Versioned {
    @EmbeddedId
    private Order.Id orderId;

    private long version;

    public PendingOrder() {}

    public PendingOrder(Order.Id orderId) {
        this.orderId = orderId;
    }

    public Order.Id getOrderId() {
        return orderId;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }
}
