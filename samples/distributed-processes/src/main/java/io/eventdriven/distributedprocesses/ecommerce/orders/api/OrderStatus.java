package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    private OrderId orderId;
    private UUID customerId;
    private PricedProductItem[] productItems;
    private double totalPrice;
    private OffsetDateTime placedAt;
    private PaymentId paymentId;
    private @Nullable String carrier;
    private @Nullable String trackingNumber;
    private OrderProgress progress;
    private Stage stage;
    private Payment payment;
    private Shipment shipment;
    private long version;

    public OrderStatus() {}

    OrderStatus(
            OrderId orderId,
            UUID customerId,
            PricedProductItem[] productItems,
            double totalPrice,
            OffsetDateTime placedAt,
            PaymentId paymentId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productItems = productItems;
        this.totalPrice = totalPrice;
        this.placedAt = placedAt;
        this.paymentId = paymentId;
        this.stage = Stage.Opened;
        this.payment = Payment.Pending;
        this.shipment = Shipment.Pending;
        this.progress = OrderProgress.AwaitingPayment;
    }

    OrderStatus withPayment(Payment payment, PaymentId paymentId) {
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

    OrderStatus ended(OrderProgress progress) {
        this.stage = Stage.Ended;
        this.progress = progress;
        return this;
    }

    private OrderStatus refreshed() {
        if (stage == Stage.Ended) return this;

        if (payment == Payment.Failed) progress = OrderProgress.PaymentFailed;
        else if (shipment == Shipment.Sent) progress = OrderProgress.Shipped;
        else if (stage == Stage.Confirmed || payment == Payment.Authorized || payment == Payment.Captured)
            progress = OrderProgress.Processing;
        else progress = OrderProgress.AwaitingPayment;

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

    public OrderId getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public PricedProductItem[] getProductItems() {
        return productItems;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public OffsetDateTime getPlacedAt() {
        return placedAt;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public @Nullable String getCarrier() {
        return carrier;
    }

    public @Nullable String getTrackingNumber() {
        return trackingNumber;
    }

    public OrderProgress getProgress() {
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
