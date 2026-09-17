package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderSummaries;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderSummary;

// The internal events say only what changed. What the outside world needs is the whole
// picture, and that is what the summary keeps — no aggregate is loaded here.
public class OrderExternalEventForwarder {
  private final OrderSummaries summaries;
  private final IntegrationEventBus eventBus;

  public OrderExternalEventForwarder(OrderSummaries summaries, IntegrationEventBus eventBus) {
    this.summaries = summaries;
    this.eventBus = eventBus;
  }

  public void on(OrderEvent.OrderInitialized event) {
    eventBus.publish(new OrderExternalEvent.OrderInitialized(
      event.orderId(),
      event.cartId(),
      event.clientId(),
      event.productItems(),
      event.totalPrice(),
      event.initializedAt()
    ));
  }

  public void on(OrderEvent.OrderConfirmed event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderConfirmed(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      event.paymentId(),
      summary.getShipmentId(),
      event.confirmedAt()
    ));
  }

  public void on(OrderEvent.OrderPaymentCaptured event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderPaymentCaptured(
      event.orderId(),
      summary.getClientId(),
      summary.getTotalPrice(),
      event.shipmentId(),
      event.amount(),
      event.capturedAt()
    ));
  }

  public void on(OrderEvent.OrderCompleted event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderCompleted(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      summary.getPaymentId(),
      summary.getShipmentId(),
      event.completedAt()
    ));
  }

  public void on(OrderEvent.OrderCancelled event) {
    eventBus.publish(new OrderExternalEvent.OrderCancelled(
      event.orderId(),
      event.paymentId(),
      event.paymentState(),
      event.shipmentId(),
      event.shipmentState(),
      event.reason(),
      event.cancelledAt()
    ));
  }

  private OrderSummary summaryOf(OrderId orderId) {
    return summaries.get(orderId)
      .orElseThrow(() -> new IllegalStateException(
        "No summary for order '%s'".formatted(orderId.value())
      ));
  }
}
