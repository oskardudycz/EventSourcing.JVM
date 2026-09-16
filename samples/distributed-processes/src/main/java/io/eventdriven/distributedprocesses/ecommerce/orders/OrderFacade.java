package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderFacade {
  private final AggregateStore<Order, OrderEvent, OrderId> store;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(OrderId orderId) {
    return "Order-%s".formatted(orderId.tail());
  }

  public OrderFacade(
    AggregateStore<Order, OrderEvent, OrderId> store,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.now = now;
  }

  public void initializeOrder(InitializeOrder command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.initialize(
        command.orderId(),
        command.cartId(),
        command.clientId(),
        command.productItems(),
        command.totalPrice(),
        now.get()
      )
    );
  }

  public void recordOrderPaymentAuthorization(RecordOrderPaymentAuthorization command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentAuthorization(command.paymentId(), command.authorizedAt())
    );
  }

  public void recordOrderStockReservation(RecordOrderStockReservation command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordStockReservation(command.shipmentId(), command.reservedAt())
    );
  }

  public void recordOrderPackageSent(RecordOrderPackageSent command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPackageSent(command.sentAt())
    );
  }

  public void recordOrderPaymentCapture(RecordOrderPaymentCapture command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentCapture(command.capturedAt())
    );
  }

  public void recordOrderDelivery(RecordOrderDelivery command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordDelivery(command.deliveredAt())
    );
  }

  public void recordOrderPaymentFailure(RecordOrderPaymentFailure command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentFailure(command.failedAt())
    );
  }

  public void recordOrderShipmentFailure(RecordOrderShipmentFailure command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordShipmentFailure(command.failedAt())
    );
  }

  public void cancelOrder(CancelOrder command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.cancel(command.cancellationReason(), now.get())
    );
  }
}
