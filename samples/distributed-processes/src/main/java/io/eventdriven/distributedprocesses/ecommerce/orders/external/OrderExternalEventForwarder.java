package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;

public class OrderExternalEventForwarder {
  private final IntegrationEventBus eventBus;

  public OrderExternalEventForwarder(IntegrationEventBus eventBus) {
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
    eventBus.publish(new OrderExternalEvent.OrderConfirmed(
      event.orderId(),
      event.shipmentId(),
      event.confirmedAt()
    ));
  }

  public void on(OrderEvent.OrderPackageSent event) {
    eventBus.publish(new OrderExternalEvent.OrderPackageSent(
      event.orderId(),
      event.paymentId(),
      event.sentAt()
    ));
  }

  public void on(OrderEvent.OrderCompleted event) {
    eventBus.publish(new OrderExternalEvent.OrderCompleted(
      event.orderId(),
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
}
