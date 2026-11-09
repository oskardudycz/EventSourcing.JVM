package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

// The internal events say only what changed. What the outside world needs is the whole
// picture, and that is what the summary keeps — no aggregate is loaded here.
public class OrderExternalEventForwarder {
  private final OrderSummaries summaries;
  private final IntegrationEventBus eventBus;

  public OrderExternalEventForwarder(OrderSummaries summaries, IntegrationEventBus eventBus) {
    this.summaries = summaries;
    this.eventBus = eventBus;
  }

  public void on(OrderEvent.OrderConfirmed event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderConfirmed(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      event.confirmedAt()
    ));
  }

  public void on(OrderEvent.OrderPackageSent event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderShipped(
      event.orderId(),
      summary.getClientId(),
      event.shipmentId(),
      event.carrier(),
      event.trackingNumber(),
      event.sentAt()
    ));
  }

  public void on(OrderEvent.OrderCompleted event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderCompleted(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      event.completedAt()
    ));
  }

  public void on(OrderEvent.OrderCancelled event) {
    var summary = summaryOf(event.orderId());

    eventBus.publish(new OrderExternalEvent.OrderCancelled(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      toPublicReason(event.reason()),
      event.cancelledAt()
    ));
  }

  private static OrderExternalEvent.OrderCancelled.Reason toPublicReason(OrderCancellationReason reason) {
    return switch (reason) {
      case ProductWasOutOfStock -> OrderExternalEvent.OrderCancelled.Reason.ProductsOutOfStock;
      case PaymentFailed -> OrderExternalEvent.OrderCancelled.Reason.PaymentFailed;
      case Requested -> OrderExternalEvent.OrderCancelled.Reason.Requested;
    };
  }

  private OrderSummary summaryOf(OrderId orderId) {
    return summaries.get(orderId)
      .orElseThrow(() -> new IllegalStateException(
        "No summary for order '%s'".formatted(orderId.value())
      ));
  }
}
