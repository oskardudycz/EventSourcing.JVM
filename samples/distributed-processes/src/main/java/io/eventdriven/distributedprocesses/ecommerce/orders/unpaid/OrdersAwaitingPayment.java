package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

// Orders whose parcel left, kept until they are paid or closed. The first payment failure after
// the dispatch starts the time the client has to pay again.
public class OrdersAwaitingPayment extends Projection<OrderAwaitingPayment> {
  public OrdersAwaitingPayment(Database database) {
    super(OrderAwaitingPayment.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      OrderPackageSent.class,
      OrderPaymentFailed.class,
      OrderCompleted.class,
      OrderClosedUnpaid.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case OrderPackageSent e -> e.orderId();
      case OrderPaymentFailed e -> e.orderId();
      case OrderCompleted e -> e.orderId();
      case OrderClosedUnpaid e -> e.orderId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable OrderAwaitingPayment evolve(@Nullable OrderAwaitingPayment current, Object event) {
    return switch (event) {
      case OrderPackageSent e -> new OrderAwaitingPayment(e.orderId(), null);
      case OrderPaymentFailed e when current != null && current.getUnpaidSince() == null ->
        new OrderAwaitingPayment(e.orderId(), e.failedAt());
      case OrderCompleted _, OrderClosedUnpaid _ -> null;
      default -> current;
    };
  }

  public List<OrderId> unpaidSinceBefore(OffsetDateTime threshold) {
    return find(order -> order.getUnpaidSince() != null && !order.getUnpaidSince().isAfter(threshold)).stream()
      .map(OrderAwaitingPayment::getOrderId)
      .toList();
  }
}
