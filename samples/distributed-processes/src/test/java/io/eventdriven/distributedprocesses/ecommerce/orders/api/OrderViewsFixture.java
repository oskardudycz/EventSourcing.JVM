package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

class OrderViewsFixture {
  final UUID customerId = UUID.randomUUID();
  final ShoppingCartId cartId = ShoppingCartId.of(customerId, 1);
  final OrderId orderId = OrderId.derivedFrom(cartId.value());
  final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  final PaymentId retriedPaymentId = PaymentId.derivedFrom(orderId.value(), 2);
  final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  final PricedProductItem[] productItems = {new PricedProductItem(UUID.randomUUID(), 2, 50)};
  final OffsetDateTime now = OffsetDateTime.now();

  private final Projection<?> view;
  private long streamPosition;

  OrderViewsFixture(Projection<?> view) {
    this.view = view;
  }

  OrderViewsFixture placed() {
    return apply(new OrderPlaced(orderId, cartId, customerId, productItems, 100, now));
  }

  OrderViewsFixture authorized(PaymentId paymentId) {
    return apply(new OrderPaymentAuthorized(orderId, paymentId, now));
  }

  OrderViewsFixture stockReserved() {
    return apply(new OrderStockReserved(orderId, shipmentId, now));
  }

  OrderViewsFixture confirmed() {
    return apply(new OrderConfirmed(orderId, paymentId, shipmentId, now));
  }

  OrderViewsFixture sent() {
    return apply(new OrderPackageSent(orderId, "UPS", "1Z999AA10123456784", now));
  }

  OrderViewsFixture captured() {
    return apply(new OrderPaymentCaptured(orderId, paymentId, 100, now));
  }

  OrderViewsFixture paymentFailed() {
    return apply(new OrderPaymentFailed(orderId, now, now.plusDays(7)));
  }

  OrderViewsFixture shipmentFailed() {
    return apply(new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, now));
  }

  OrderViewsFixture retried() {
    return apply(new OrderPaymentRetried(orderId, retriedPaymentId, 100, now));
  }

  OrderViewsFixture completed() {
    return apply(new OrderCompleted(orderId, now));
  }

  OrderViewsFixture closedUnpaid() {
    return apply(new OrderClosedUnpaid(orderId, now));
  }

  OrderViewsFixture canceled() {
    return apply(new OrderCanceled(
      orderId, null, OrderPaymentStatus.NotAuthorized, shipmentId, OrderShipmentStatus.Reserved,
      OrderCancellationReason.Requested, now
    ));
  }

  private OrderViewsFixture apply(Object event) {
    var position = streamPosition++;
    if (view.canHandle().contains(event.getClass()))
      view.handle(new EventEnvelope<>(event, new EventMetadata(OrderFacade.mapToStreamId(orderId), position, position)));
    return this;
  }
}
