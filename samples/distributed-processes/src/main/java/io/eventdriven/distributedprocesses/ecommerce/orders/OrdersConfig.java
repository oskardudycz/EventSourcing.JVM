package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderSummaries;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentAuthorized;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentCaptured;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasDelivered;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.StockReservationFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.StockReserved;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartFinalized;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

public final class OrdersConfig {
  private OrdersConfig() {
  }

  public static OrderFacade configure(
    EventStore eventStore,
    MessageBus messageBus,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Order, OrderEvent, OrderId>(
      eventStore,
      OrderFacade::mapToStreamId,
      Order::empty
    );

    var facade = new OrderFacade(store, now);

    messageBus
      .subscribe(InitializeOrder.class, facade::initializeOrder)
      .subscribe(RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
      .subscribe(RecordOrderStockReservation.class, facade::recordOrderStockReservation)
      .subscribe(RecordOrderPackageSent.class, facade::recordOrderPackageSent)
      .subscribe(RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
      .subscribe(RecordOrderDelivery.class, facade::recordOrderDelivery)
      .subscribe(RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
      .subscribe(RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
      .subscribe(CancelOrder.class, facade::cancelOrder);

    var database = new Database();
    var summaries = new OrderSummaries(database);

    eventStore
      .subscribe(OrderInitialized.class, summaries::onInitialized)
      .subscribe(OrderPaymentAuthorized.class, summaries::onPaymentAuthorized)
      .subscribe(OrderStockReserved.class, summaries::onStockReserved)
      .subscribe(OrderConfirmed.class, summaries::onConfirmed)
      .subscribe(OrderPaymentCaptured.class, summaries::onPaymentCaptured)
      .subscribe(OrderPackageSent.class, summaries::onPackageSent)
      .subscribe(OrderPaymentFailed.class, summaries::onPaymentFailed)
      .subscribe(OrderShipmentFailed.class, summaries::onShipmentFailed)
      .subscribe(OrderCompleted.class, summaries::onCompleted)
      .subscribe(OrderCancelled.class, summaries::onCancelled);

    var pendingOrders = new PendingOrders(database);

    eventStore
      .subscribe(OrderInitialized.class, pendingOrders::onInitialized)
      .subscribe(OrderPaymentAuthorized.class, pendingOrders::onPaymentAuthorized)
      .subscribe(OrderPaymentCaptured.class, pendingOrders::onPaymentCaptured)
      .subscribe(OrderPaymentFailed.class, pendingOrders::onPaymentFailed)
      .subscribe(OrderStockReserved.class, pendingOrders::onStockReserved)
      .subscribe(OrderPackageSent.class, pendingOrders::onPackageSent)
      .subscribe(OrderShipmentFailed.class, pendingOrders::onShipmentFailed)
      .subscribe(OrderCompleted.class, pendingOrders::onCompleted)
      .subscribe(OrderCancelled.class, pendingOrders::onCancelled);

    var forwarder = new OrderExternalEventForwarder(summaries, messageBus);

    eventStore
      .subscribe(OrderConfirmed.class, forwarder::onConfirmed)
      .subscribe(OrderPackageSent.class, forwarder::onPackageSent)
      .subscribe(OrderCompleted.class, forwarder::onCompleted)
      .subscribe(OrderCancelled.class, forwarder::onCancelled);

    return facade;
  }

  public static void configureProcess(
    EventStore eventStore,
    MessageBus messageBus
  ) {
    var saga = new OrderSaga(messageBus);

    eventStore
      .subscribe(OrderInitialized.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderConfirmed.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderPackageSent.class, envelope -> saga.on(envelope.data()))
      .subscribe(OrderCancelled.class, envelope -> saga.on(envelope.data()));

    messageBus
      .subscribe(ShoppingCartFinalized.class, saga::on)
      .subscribe(PaymentAuthorized.class, saga::on)
      .subscribe(StockReserved.class, saga::on)
      .subscribe(PackageWasSent.class, saga::on)
      .subscribe(PaymentCaptured.class, saga::on)
      .subscribe(PackageWasDelivered.class, saga::on)
      .subscribe(PaymentFailed.class, saga::on)
      .subscribe(StockReservationFailed.class, saga::on);
  }
}
