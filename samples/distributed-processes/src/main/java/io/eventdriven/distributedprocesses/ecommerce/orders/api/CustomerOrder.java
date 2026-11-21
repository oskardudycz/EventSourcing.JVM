package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class CustomerOrder implements Versioned {
    @EmbeddedId
    private OrderId orderId;

    private UUID customerId;
    private OffsetDateTime placedAt;
    private double totalPrice;

    @Enumerated(EnumType.STRING)
    private OrderProgress progress;

    private boolean shipped;
    private long version;

    public CustomerOrder() {}

    public CustomerOrder(
            OrderId orderId,
            UUID customerId,
            OffsetDateTime placedAt,
            double totalPrice,
            OrderProgress progress,
            boolean shipped) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.placedAt = placedAt;
        this.totalPrice = totalPrice;
        this.progress = progress;
        this.shipped = shipped;
    }

    CustomerOrder with(OrderProgress progress, boolean shipped) {
        return new CustomerOrder(orderId, customerId, placedAt, totalPrice, progress, shipped);
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OffsetDateTime getPlacedAt() {
        return placedAt;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public OrderProgress getProgress() {
        return progress;
    }

    boolean hasShipped() {
        return shipped;
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
