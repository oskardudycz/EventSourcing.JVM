package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderFacade {
  private final AggregateStore<Order, OrderEvent, OrderId> store;
  private final PaymentTerms paymentTerms;
  private final Supplier<OffsetDateTime> now;

  public static String mapToStreamId(OrderId orderId) {
    return "Order-%s".formatted(orderId.tail());
  }

  public OrderFacade(
    AggregateStore<Order, OrderEvent, OrderId> store,
    PaymentTerms paymentTerms,
    Supplier<OffsetDateTime> now
  ) {
    this.store = store;
    this.paymentTerms = paymentTerms;
    this.now = now;
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void placeOrder(PlaceOrder command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.place(
        command.orderId(),
        command.cartId(),
        command.customerId(),
        command.productItems(),
        command.totalPrice(),
        now.get()
      )
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderPaymentAuthorization(RecordOrderPaymentAuthorization command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentAuthorization(command.paymentId(), command.authorizedAt())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderStockReservation(RecordOrderStockReservation command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordStockReservation(command.shipmentId(), command.reservedAt())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderPackageSent(RecordOrderPackageSent command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPackageSent(
        command.carrier(),
        command.trackingNumber(),
        command.sentAt()
      )
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderPaymentCapture(RecordOrderPaymentCapture command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentCapture(command.capturedAt())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderPaymentFailure(RecordOrderPaymentFailure command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordPaymentFailure(command.failedAt(), paymentTerms.dueAt(command.failedAt()))
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void recordOrderShipmentFailure(RecordOrderShipmentFailure command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.recordShipmentFailure(command.reason(), command.failedAt())
    );
  }

  public void retryOrderPayment(RetryOrderPayment command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.retryPayment(now.get())
    );
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.internal))
  public void closeUnpaidOrder(CloseUnpaidOrder command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.closeUnpaid(now.get())
    );
  }

  public void cancelOrder(CancelOrder command) {
    store.getAndUpdate(
      command.orderId(),
      current -> current.cancel(command.cancellationReason(), now.get())
    );
  }
}
