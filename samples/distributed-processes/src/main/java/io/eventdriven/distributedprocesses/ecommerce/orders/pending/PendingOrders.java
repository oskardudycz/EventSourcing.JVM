package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

// Orders that are still running: a to-do list for whoever has to chase them,
// and the source for cancelling the ones that waited too long.
public class PendingOrders {
  private final Database database;

  public PendingOrders(Database database) {
    this.database = database;
  }

  public void onInitialized(EventEnvelope<OrderInitialized> event) {
    database.store(
      PendingOrder.class,
      event.data().orderId(),
      event.metadata().streamPosition(),
      new PendingOrder(
        event.data().orderId(),
        event.data().clientId(),
        event.data().totalPrice(),
        event.data().initializedAt(),
        OrderPaymentStatus.NotAuthorized,
        OrderShipmentStatus.NotReserved
      )
    );
  }

  public void onPaymentAuthorized(EventEnvelope<OrderPaymentAuthorized> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setPaymentStatus(OrderPaymentStatus.Authorized);

        return pending;
      });
  }

  public void onPaymentCaptured(EventEnvelope<OrderPaymentCaptured> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setPaymentStatus(OrderPaymentStatus.Captured);

        return pending;
      });
  }

  public void onPaymentFailed(EventEnvelope<OrderPaymentFailed> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setPaymentStatus(OrderPaymentStatus.NotAuthorized);

        return pending;
      });
  }

  public void onStockReserved(EventEnvelope<OrderStockReserved> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setShipmentStatus(OrderShipmentStatus.Reserved);

        return pending;
      });
  }

  public void onPackageSent(EventEnvelope<OrderPackageSent> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setShipmentStatus(OrderShipmentStatus.Sent);

        return pending;
      });
  }

  public void onShipmentFailed(EventEnvelope<OrderShipmentFailed> event) {
    database.getAndUpdate(
      PendingOrder.class, event.data().orderId(), event.metadata().streamPosition(),
      pending -> {
        pending.setShipmentStatus(OrderShipmentStatus.NotReserved);

        return pending;
      });
  }

  public void onCompleted(EventEnvelope<OrderCompleted> event) {
    database.delete(PendingOrder.class, event.data().orderId());
  }

  public void onCancelled(EventEnvelope<OrderCancelled> event) {
    database.delete(PendingOrder.class, event.data().orderId());
  }

  public List<PendingOrder> all() {
    return database.find(PendingOrder.class, pending -> true);
  }

  public List<OrderId> olderThan(OffsetDateTime threshold) {
    return all().stream()
      .filter(pending -> pending.getInitializedAt().isBefore(threshold))
      .map(PendingOrder::getOrderId)
      .toList();
  }
}
