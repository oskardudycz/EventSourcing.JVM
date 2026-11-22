package io.eventdriven.ecommerce.orders.getorderstatus;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.orders.Order;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
public class OrderStatus implements Versioned {
    enum Stage {
        Opened,
        Confirmed,
        Ended
    }

    enum Payment {
        Pending,
        Authorized,
        Captured,
        Failed
    }

    enum Shipment {
        Pending,
        Reserved,
        Sent,
        Failed
    }

    @EmbeddedId
    private Order.Id orderId;

    private UUID customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Order.PricedProductItem> productItems;

    private double totalPrice;
    private OffsetDateTime placedAt;
    private io.eventdriven.ecommerce.payments.Payment.Id paymentId;
    private @Nullable String carrier;
    private @Nullable String trackingNumber;

    @Enumerated(EnumType.STRING)
    private Order.Progress progress;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private Shipment shipment;

    private long version;

    public OrderStatus() {}

    OrderStatus(
            Order.Id orderId,
            UUID customerId,
            Order.PricedProductItem[] productItems,
            double totalPrice,
            OffsetDateTime placedAt,
            io.eventdriven.ecommerce.payments.Payment.Id paymentId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productItems = List.of(productItems);
        this.totalPrice = totalPrice;
        this.placedAt = placedAt;
        this.paymentId = paymentId;
        this.stage = Stage.Opened;
        this.payment = Payment.Pending;
        this.shipment = Shipment.Pending;
        this.progress = Order.Progress.AwaitingPayment;
    }

    OrderStatus withPayment(Payment payment, io.eventdriven.ecommerce.payments.Payment.Id paymentId) {
        this.payment = payment;
        this.paymentId = paymentId;
        return refreshed();
    }

    OrderStatus withPayment(Payment payment) {
        return withPayment(payment, paymentId);
    }

    OrderStatus withShipment(Shipment shipment) {
        this.shipment = shipment;
        return refreshed();
    }

    OrderStatus sent(String carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        return withShipment(Shipment.Sent);
    }

    OrderStatus confirmed() {
        this.stage = Stage.Confirmed;
        return refreshed();
    }

    OrderStatus ended(Order.Progress progress) {
        this.stage = Stage.Ended;
        this.progress = progress;
        return this;
    }

    private OrderStatus refreshed() {
        if (stage == Stage.Ended) return this;

        if (payment == Payment.Failed) progress = Order.Progress.PaymentFailed;
        else if (shipment == Shipment.Failed) progress = Order.Progress.OutOfStock;
        else if (shipment == Shipment.Sent) progress = Order.Progress.Shipped;
        else if (stage == Stage.Confirmed || payment == Payment.Authorized || payment == Payment.Captured)
            progress = Order.Progress.Processing;
        else progress = Order.Progress.AwaitingPayment;

        return this;
    }

    public List<OrderAction> getActions() {
        var actions = new ArrayList<OrderAction>();
        var canStillBePaid = (stage == Stage.Opened || stage == Stage.Confirmed && shipment == Shipment.Sent)
                && shipment != Shipment.Failed;

        if (canStillBePaid && payment == Payment.Pending) actions.add(OrderAction.PayNow);
        if (canStillBePaid && payment == Payment.Failed) actions.add(OrderAction.RetryPayment);
        if (stage == Stage.Opened) actions.add(OrderAction.CancelOrder);

        return actions;
    }

    public Order.Id getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Order.PricedProductItem[] getProductItems() {
        return productItems.toArray(new Order.PricedProductItem[0]);
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public OffsetDateTime getPlacedAt() {
        return placedAt;
    }

    public io.eventdriven.ecommerce.payments.Payment.Id getPaymentId() {
        return paymentId;
    }

    public @Nullable String getCarrier() {
        return carrier;
    }

    public @Nullable String getTrackingNumber() {
        return trackingNumber;
    }

    public Order.Progress getProgress() {
        return progress;
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
