package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

import java.util.List;

public class OrderExternalEventForwarder {
  private final OrderSummaries summaries;
  private final MessageBus messageBus;

  public OrderExternalEventForwarder(OrderSummaries summaries, MessageBus messageBus) {
    this.summaries = summaries;
    this.messageBus = messageBus;
  }

  public void onConfirmed(EventEnvelope<OrderEvent.OrderConfirmed> envelope) {
    var event = envelope.data();
    var summary = summaryOf(event.orderId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new OrderExternalEvent.OrderConfirmed(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      event.confirmedAt()
    )));
  }

  public void onPackageSent(EventEnvelope<OrderEvent.OrderPackageSent> envelope) {
    var event = envelope.data();
    var summary = summaryOf(event.orderId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new OrderExternalEvent.OrderShipped(
      event.orderId(),
      summary.getClientId(),
      event.shipmentId(),
      event.carrier(),
      event.trackingNumber(),
      event.sentAt()
    )));
  }

  public void onCompleted(EventEnvelope<OrderEvent.OrderCompleted> envelope) {
    var event = envelope.data();
    var summary = summaryOf(event.orderId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new OrderExternalEvent.OrderCompleted(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      event.completedAt()
    )));
  }

  public void onCancelled(EventEnvelope<OrderEvent.OrderCancelled> envelope) {
    var event = envelope.data();
    var summary = summaryOf(event.orderId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new OrderExternalEvent.OrderCancelled(
      event.orderId(),
      summary.getClientId(),
      summary.getProductItems(),
      summary.getTotalPrice(),
      toPublicReason(event.reason()),
      event.cancelledAt()
    )));
  }

  private static OrderExternalEvent.OrderCancelled.Reason toPublicReason(OrderCancellationReason reason) {
    return switch (reason) {
      case ProductWasOutOfStock -> OrderExternalEvent.OrderCancelled.Reason.ProductsOutOfStock;
      case PaymentFailed -> OrderExternalEvent.OrderCancelled.Reason.PaymentFailed;
      case Requested -> OrderExternalEvent.OrderCancelled.Reason.Requested;
    };
  }

  private OrderSummary summaryOf(OrderId orderId, long streamPosition) {
    return summaries.get(orderId)
      .filter(summary -> summary.getVersion() >= streamPosition)
      .orElseThrow(() -> new IllegalStateException(
        "Summary of order '%s' has not reached position %d yet".formatted(orderId.value(), streamPosition)
      ));
  }
}
