package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatus.Payment;
import static io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatus.Shipment;

public class OrderStatuses extends Projection<OrderStatus> {
  public OrderStatuses(Database database) {
    super(OrderStatus.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      OrderPlaced.class,
      OrderPaymentAuthorized.class,
      OrderStockReserved.class,
      OrderConfirmed.class,
      OrderPackageSent.class,
      OrderPaymentCaptured.class,
      OrderPaymentFailed.class,
      OrderShipmentFailed.class,
      OrderPaymentRetried.class,
      OrderCompleted.class,
      OrderCanceled.class,
      OrderClosedUnpaid.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case OrderPlaced e -> e.orderId();
      case OrderPaymentAuthorized e -> e.orderId();
      case OrderStockReserved e -> e.orderId();
      case OrderConfirmed e -> e.orderId();
      case OrderPackageSent e -> e.orderId();
      case OrderPaymentCaptured e -> e.orderId();
      case OrderPaymentFailed e -> e.orderId();
      case OrderShipmentFailed e -> e.orderId();
      case OrderPaymentRetried e -> e.orderId();
      case OrderCompleted e -> e.orderId();
      case OrderCanceled e -> e.orderId();
      case OrderClosedUnpaid e -> e.orderId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable OrderStatus evolve(@Nullable OrderStatus current, Object event) {
    return switch (event) {
      case OrderPlaced e -> new OrderStatus(
        e.orderId(),
        e.customerId(),
        e.productItems(),
        e.totalPrice(),
        e.placedAt(),
        PaymentId.derivedFrom(e.orderId().value())
      );
      case OrderPaymentAuthorized e -> current.withPayment(Payment.Authorized, e.paymentId());
      case OrderStockReserved _ -> current.withShipment(Shipment.Reserved);
      case OrderConfirmed _ -> current.confirmed();
      case OrderPackageSent e -> current.sent(e.carrier(), e.trackingNumber());
      case OrderPaymentCaptured _ -> current.withPayment(Payment.Captured);
      case OrderPaymentFailed _ -> current.withPayment(Payment.Failed);
      case OrderShipmentFailed _ -> current.withShipment(Shipment.Failed);
      case OrderPaymentRetried e -> current.withPayment(Payment.Pending, e.paymentId());
      case OrderCompleted _ -> current.ended(OrderProgress.Completed);
      case OrderCanceled _ -> current.ended(OrderProgress.Canceled);
      case OrderClosedUnpaid _ -> current.ended(OrderProgress.ClosedUnpaid);
      default -> current;
    };
  }
}
