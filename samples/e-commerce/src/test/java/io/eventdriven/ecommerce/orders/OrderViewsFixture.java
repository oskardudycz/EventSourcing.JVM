package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.messaging.Metadata;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class OrderViewsFixture {
    public final UUID customerId = UUID.randomUUID();
    public final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);
    public final Order.Id orderId = Order.Id.derivedFrom(cartId.value());
    public final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());
    public final Payment.Id retriedPaymentId = Payment.Id.derivedFrom(orderId.value(), 2);
    public final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderId.value());
    public final Order.PricedProductItem[] productItems = {new Order.PricedProductItem(UUID.randomUUID(), 2, 50)};
    public final OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);

    private final Projection<?, ?> view;
    private long streamPosition;

    public OrderViewsFixture(Projection<?, ?> view) {
        this.view = view;
    }

    public OrderViewsFixture placed() {
        return apply(new OrderPlaced(orderId, cartId, customerId, productItems, 100, now));
    }

    public OrderViewsFixture authorized(Payment.Id paymentId) {
        return apply(new OrderPaymentAuthorized(orderId, paymentId, now));
    }

    public OrderViewsFixture stockReserved() {
        return apply(new OrderStockReserved(orderId, shipmentId, now));
    }

    public OrderViewsFixture confirmed() {
        return apply(new OrderConfirmed(orderId, paymentId, shipmentId, now));
    }

    public OrderViewsFixture sent() {
        return apply(new OrderPackageSent(orderId, "UPS", "1Z999AA10123456784", now));
    }

    public OrderViewsFixture captured() {
        return apply(new OrderPaymentCaptured(orderId, paymentId, 100, now));
    }

    public OrderViewsFixture paymentFailed() {
        return apply(new OrderPaymentFailed(orderId, now, now.plusDays(7)));
    }

    public OrderViewsFixture shipmentFailed() {
        return apply(new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, now));
    }

    public OrderViewsFixture retried() {
        return apply(new OrderPaymentRetried(orderId, retriedPaymentId, 100, now));
    }

    public OrderViewsFixture completed() {
        return apply(new OrderCompleted(orderId, now));
    }

    public OrderViewsFixture closedUnpaid() {
        return apply(new OrderClosedUnpaid(orderId, now));
    }

    public OrderViewsFixture canceled() {
        return apply(new OrderCanceled(
                orderId,
                null,
                Order.PaymentStatus.NotAuthorized,
                shipmentId,
                Order.ShipmentStatus.Reserved,
                Order.CancellationReason.Requested,
                now));
    }

    private OrderViewsFixture apply(Object event) {
        var position = streamPosition++;
        if (view.canHandle().contains(event.getClass()))
            view.handle(new MessageEnvelope<>(event, Metadata.at(OrderFacade.mapToStreamId(orderId), position)));
        return this;
    }
}
