package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderCancelled;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderCompleted;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderInitialized;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderPaymentRecorded;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Order extends AbstractAggregate<OrderEvent, UUID> {
  public enum Status {
    Opened,
    Paid,
    Completed,
    Cancelled
  }

  private UUID clientId;

  private PricedProductItem[] productItems;

  private double totalPrice;

  private Status status;

  private UUID paymentId;

  public static Order initialize(
    UUID orderId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime now
  ) {
    return new Order(
      orderId,
      clientId,
      productItems,
      totalPrice,
      now
    );
  }

  private Order(UUID id, UUID clientId, PricedProductItem[] productItems, double totalPrice, OffsetDateTime now) {
    enqueue(new OrderInitialized(
      id,
      clientId,
      productItems,
      totalPrice,
      now
    ));
  }

  public void recordPayment(UUID paymentId, OffsetDateTime recordedAt) {
    enqueue(new OrderPaymentRecorded(
      id,
      paymentId,
      productItems,
      totalPrice,
      recordedAt
    ));
  }

  public void complete(OffsetDateTime now) {
    if (status != Status.Paid)
      throw new IllegalStateException("Cannot complete a not paid order.");

    enqueue(new OrderCompleted(id, now));
  }

  public void cancel(OrderCancellationReason cancellationReason, OffsetDateTime now) {
    if (status == Status.Opened || status == Status.Cancelled)
      throw new IllegalStateException("Cannot cancel a closed order.");

    enqueue(new OrderCancelled(id, paymentId, cancellationReason, now));
  }

  @Override
  public void when(OrderEvent event) {
    switch (event) {
      case OrderInitialized orderInitialized -> {
        id = orderInitialized.orderId();
        clientId = orderInitialized.clientId();
        productItems = orderInitialized.productItems();
        status = Status.Opened;
      }
      case OrderPaymentRecorded paymentRecorded -> {
        paymentId = paymentRecorded.paymentId();
        status = Status.Paid;
      }
      case OrderCompleted completed -> status = Status.Completed;
      case OrderCancelled cancelled -> status = Status.Cancelled;
    }
  }
}
