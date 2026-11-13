package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.UnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentAuthorized;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentCaptured;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.ShipmentFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.StockReserved;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

public final class OrdersConfig {
  private OrdersConfig() {
  }

  public static OrdersModule configure(
    EventStore eventStore,
    MessageBus messageBus,
    Duration paymentRetryPeriod,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Order, OrderEvent, OrderId>(
      eventStore,
      OrderFacade::mapToStreamId,
      Order::empty
    );

    var facade = new OrderFacade(store, now);

    messageBus
      .subscribe(PlaceOrder.class, facade::placeOrder)
      .subscribe(RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
      .subscribe(RecordOrderStockReservation.class, facade::recordOrderStockReservation)
      .subscribe(RecordOrderPackageSent.class, facade::recordOrderPackageSent)
      .subscribe(RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
      .subscribe(RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
      .subscribe(RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
      .subscribe(RetryOrderPayment.class, facade::retryOrderPayment)
      .subscribe(CloseUnpaidOrder.class, facade::closeUnpaidOrder)
      .subscribe(CancelOrder.class, facade::cancelOrder);

    var database = new Database();
    var pendingOrders = new PendingOrders(database);
    var ordersAwaitingPayment = new OrdersAwaitingPayment(database);

    for (var eventType : pendingOrders.canHandle()) {
      eventStore.subscribe(eventType, pendingOrders::handle);
    }

    for (var eventType : ordersAwaitingPayment.canHandle()) {
      eventStore.subscribe(eventType, ordersAwaitingPayment::handle);
    }

    return new OrdersModule(
      facade,
      pendingOrders,
      new ScheduledUnpaidOrdersWorker(new UnpaidOrdersWorker(ordersAwaitingPayment, messageBus, paymentRetryPeriod), now)
    );
  }

  public static void configureProcess(
    EventStore eventStore,
    MessageBus messageBus
  ) {
    var saga = new OrderSaga(messageBus);

    eventStore
      .subscribe(OrderPlaced.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderConfirmed.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderPaymentDue.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderPaymentRetried.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderCancelled.class, envelope -> saga.on(envelope.data()));

    messageBus
      .subscribe(ShoppingCartCheckedOut.class, saga::on)
      .subscribe(PaymentAuthorized.class, saga::on)
      .subscribe(StockReserved.class, saga::on)
      .subscribe(PackageWasSent.class, saga::on)
      .subscribe(PaymentCaptured.class, saga::on)
      .subscribe(PaymentFailed.class, saga::on)
      .subscribe(ShipmentFailed.class, saga::on);
  }
}
