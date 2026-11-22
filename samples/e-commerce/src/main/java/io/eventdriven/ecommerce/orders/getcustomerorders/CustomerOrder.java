package io.eventdriven.ecommerce.orders.getcustomerorders;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.orders.Order;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class CustomerOrder implements Versioned {
    @EmbeddedId
    private Order.Id orderId;

    private UUID customerId;
    private OffsetDateTime placedAt;
    private double totalPrice;

    @Enumerated(EnumType.STRING)
    private Order.Progress progress;

    private boolean shipped;
    private long version;

    public CustomerOrder() {}

    public CustomerOrder(
            Order.Id orderId,
            UUID customerId,
            OffsetDateTime placedAt,
            double totalPrice,
            Order.Progress progress,
            boolean shipped) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.placedAt = placedAt;
        this.totalPrice = totalPrice;
        this.progress = progress;
        this.shipped = shipped;
    }

    CustomerOrder with(Order.Progress progress, boolean shipped) {
        return new CustomerOrder(orderId, customerId, placedAt, totalPrice, progress, shipped);
    }

    public Order.Id getOrderId() {
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

    public Order.Progress getProgress() {
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
