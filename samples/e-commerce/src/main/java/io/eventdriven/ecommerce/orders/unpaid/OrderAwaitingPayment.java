package io.eventdriven.ecommerce.orders.unpaid;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.orders.Order;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
public class OrderAwaitingPayment implements Versioned {
    @EmbeddedId
    private Order.Id orderId;

    private OffsetDateTime dueAt;
    private long version;

    public OrderAwaitingPayment() {}

    public OrderAwaitingPayment(Order.Id orderId, OffsetDateTime dueAt) {
        this.orderId = orderId;
        this.dueAt = dueAt;
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
