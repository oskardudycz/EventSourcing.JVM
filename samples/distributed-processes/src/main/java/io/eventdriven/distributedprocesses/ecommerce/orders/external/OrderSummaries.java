package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.Order;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;

import java.util.Optional;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

// Everything the outside world is told about an order, kept where the forwarder can read it
// without loading the aggregate.
public class OrderSummaries {
  private final Database database;

  public OrderSummaries(Database database) {
    this.database = database;
  }

  public void onInitialized(EventEnvelope<OrderInitialized> event) {
    database.store(
      OrderSummary.class,
      event.data().orderId(),
      event.metadata().streamPosition(),
      new OrderSummary(
        event.data().orderId(),
        event.data().cartId(),
        event.data().clientId(),
        event.data().productItems(),
        event.data().totalPrice(),
        Order.Status.Opened,
        OrderPaymentStatus.NotAuthorized,
        OrderShipmentStatus.NotReserved
      )
    );
  }

  public void onPaymentAuthorized(EventEnvelope<OrderPaymentAuthorized> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setPaymentId(event.data().paymentId());
        summary.setPaymentStatus(OrderPaymentStatus.Authorized);

        return summary;
      });
  }

  public void onStockReserved(EventEnvelope<OrderStockReserved> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setShipmentId(event.data().shipmentId());
        summary.setShipmentStatus(OrderShipmentStatus.Reserved);

        return summary;
      });
  }

  public void onConfirmed(EventEnvelope<OrderConfirmed> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(Order.Status.Confirmed);

        return summary;
      });
  }

  public void onPaymentCaptured(EventEnvelope<OrderPaymentCaptured> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setPaymentStatus(OrderPaymentStatus.Captured);

        return summary;
      });
  }

  public void onPackageSent(EventEnvelope<OrderPackageSent> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setShipmentStatus(OrderShipmentStatus.Sent);

        return summary;
      });
  }

  public void onPaymentFailed(EventEnvelope<OrderPaymentFailed> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setPaymentStatus(OrderPaymentStatus.NotAuthorized);

        return summary;
      });
  }

  public void onShipmentFailed(EventEnvelope<OrderShipmentFailed> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setShipmentStatus(OrderShipmentStatus.NotReserved);

        return summary;
      });
  }

  public void onCompleted(EventEnvelope<OrderCompleted> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(Order.Status.Completed);

        return summary;
      });
  }

  public void onCancelled(EventEnvelope<OrderCancelled> event) {
    database.getAndUpdate(
      OrderSummary.class, event.data().orderId(), event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(Order.Status.Cancelled);
        summary.setPaymentId(event.data().paymentId());
        summary.setPaymentStatus(event.data().paymentStatus());
        summary.setShipmentId(event.data().shipmentId());
        summary.setShipmentStatus(event.data().shipmentStatus());

        return summary;
      });
  }

  public Optional<OrderSummary> get(OrderId orderId) {
    return database.get(OrderSummary.class, orderId);
  }
}
