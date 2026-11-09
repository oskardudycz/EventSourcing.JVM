package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.cancelling.OrderCancellingPolicy;
import io.eventdriven.distributedprocesses.ecommerce.orders.cancelling.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderSummaries;
import io.eventdriven.distributedprocesses.ecommerce.orders.fulfilling.OrderFulfillingPolicy;
import io.eventdriven.distributedprocesses.ecommerce.orders.placing.OrderPlacingPolicy;
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
    CommandBus commandBus,
    EventStore eventStore,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Order, OrderEvent, OrderId>(
      eventStore,
      OrderFacade::mapToStreamId,
      Order::empty
    );

    var facade = new OrderFacade(store, now);

    commandBus
      .handle(InitializeOrder.class, facade::initializeOrder)
      .handle(RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
      .handle(RecordOrderStockReservation.class, facade::recordOrderStockReservation)
      .handle(RecordOrderPackageSent.class, facade::recordOrderPackageSent)
      .handle(RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
      .handle(RecordOrderDelivery.class, facade::recordOrderDelivery)
      .handle(RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
      .handle(RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
      .handle(CancelOrder.class, facade::cancelOrder);

    var database = new Database();
    var summaries = new OrderSummaries(database);

    // The projections go first, so that what the forwarder reads already has the event in it.
    internalEventBus
      .subscribeWithMetadata(OrderInitialized.class, summaries::onInitialized)
      .subscribeWithMetadata(OrderPaymentAuthorized.class, summaries::onPaymentAuthorized)
      .subscribeWithMetadata(OrderStockReserved.class, summaries::onStockReserved)
      .subscribeWithMetadata(OrderConfirmed.class, summaries::onConfirmed)
      .subscribeWithMetadata(OrderPaymentCaptured.class, summaries::onPaymentCaptured)
      .subscribeWithMetadata(OrderPackageSent.class, summaries::onPackageSent)
      .subscribeWithMetadata(OrderPaymentFailed.class, summaries::onPaymentFailed)
      .subscribeWithMetadata(OrderShipmentFailed.class, summaries::onShipmentFailed)
      .subscribeWithMetadata(OrderCompleted.class, summaries::onCompleted)
      .subscribeWithMetadata(OrderCancelled.class, summaries::onCancelled);

    var pendingOrders = new PendingOrders(database);

    internalEventBus
      .subscribeWithMetadata(OrderInitialized.class, pendingOrders::onInitialized)
      .subscribeWithMetadata(OrderPaymentAuthorized.class, pendingOrders::onPaymentAuthorized)
      .subscribeWithMetadata(OrderPaymentCaptured.class, pendingOrders::onPaymentCaptured)
      .subscribeWithMetadata(OrderPaymentFailed.class, pendingOrders::onPaymentFailed)
      .subscribeWithMetadata(OrderStockReserved.class, pendingOrders::onStockReserved)
      .subscribeWithMetadata(OrderPackageSent.class, pendingOrders::onPackageSent)
      .subscribeWithMetadata(OrderShipmentFailed.class, pendingOrders::onShipmentFailed)
      .subscribeWithMetadata(OrderCompleted.class, pendingOrders::onCompleted)
      .subscribeWithMetadata(OrderCancelled.class, pendingOrders::onCancelled);

    var forwarder = new OrderExternalEventForwarder(summaries, integrationEventBus);

    internalEventBus
      .subscribe(OrderConfirmed.class, forwarder::on)
      .subscribe(OrderPackageSent.class, forwarder::on)
      .subscribe(OrderCompleted.class, forwarder::on)
      .subscribe(OrderCancelled.class, forwarder::on);

    return facade;
  }

  // The policies send commands to Payments and Shipments, so they can only be wired once those
  // modules handle them, and after the forwarder, so that each public event goes out before
  // the steps it causes.
  public static void configureProcess(
    CommandBus commandBus,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus
  ) {
    var placing = new OrderPlacingPolicy(commandBus);
    var fulfilling = new OrderFulfillingPolicy(commandBus);
    var cancelling = new OrderCancellingPolicy(commandBus);

    internalEventBus
      .subscribe(OrderInitialized.class, placing::on)
      .subscribe(OrderConfirmed.class, fulfilling::on)
      .subscribe(OrderPackageSent.class, fulfilling::on)
      .subscribe(OrderCancelled.class, cancelling::on);

    integrationEventBus
      .subscribe(ShoppingCartFinalized.class, placing::on)
      .subscribe(PaymentAuthorized.class, placing::on)
      .subscribe(StockReserved.class, placing::on)
      .subscribe(PackageWasSent.class, fulfilling::on)
      .subscribe(PaymentCaptured.class, fulfilling::on)
      .subscribe(PackageWasDelivered.class, fulfilling::on)
      .subscribe(PaymentFailed.class, cancelling::on)
      .subscribe(StockReservationFailed.class, cancelling::on);
  }
}
