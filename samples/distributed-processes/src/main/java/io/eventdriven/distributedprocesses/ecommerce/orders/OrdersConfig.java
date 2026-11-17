package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
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

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

public final class OrdersConfig {
  public static final String internal = "orders-internal";
  public static final String shoppingCartsExternal = "orders-shopping_carts-external";
  public static final String paymentsResponses = "orders-payments-responses";
  public static final String shipmentsResponses = "orders-shipments-responses";
  public static final String responsesForOrders = "ecommerce.order.*";
  private OrdersConfig() {
  }

  public static OrdersModule configure(
    EventStore eventStore,
    MessageBus messageBus,
    PaymentTerms paymentTerms,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Order, OrderEvent, OrderId>(
      eventStore,
      OrderFacade::mapToStreamId,
      Order::empty
    );

    var facade = new OrderFacade(store, paymentTerms, now);

    messageBus
      .routes(new MessageRoutes()
        .routeTarget(MessageRoutes.inPackage(OrdersConfig.class.getPackageName()), internal))
      .bind(internal, internal, "#")
      .subscribe(internal, PlaceOrder.class, facade::placeOrder)
      .subscribe(internal, RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
      .subscribe(internal, RecordOrderStockReservation.class, facade::recordOrderStockReservation)
      .subscribe(internal, RecordOrderPackageSent.class, facade::recordOrderPackageSent)
      .subscribe(internal, RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
      .subscribe(internal, RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
      .subscribe(internal, RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
      .subscribe(internal, CloseUnpaidOrder.class, facade::closeUnpaidOrder);

    var database = new Database();
    var pendingOrders = new PendingOrders(database);
    var ordersAwaitingPayment = new OrdersAwaitingPayment(database);

    for (var eventType : pendingOrders.canHandle()) {
      eventStore.subscribe(eventType, pendingOrders::handle);
    }

    for (var eventType : ordersAwaitingPayment.canHandle()) {
      eventStore.subscribe(eventType, ordersAwaitingPayment::handle);
    }

    var customerOrders = new CustomerOrders(new Database());
    var orderStatuses = new OrderStatuses(new Database());

    for (var eventType : customerOrders.canHandle()) {
      eventStore.subscribe(eventType, customerOrders::handle);
    }

    for (var eventType : orderStatuses.canHandle()) {
      eventStore.subscribe(eventType, orderStatuses::handle);
    }

    return new OrdersModule(
      facade,
      pendingOrders,
      new ScheduledUnpaidOrdersWorker(new UnpaidOrdersWorker(ordersAwaitingPayment, messageBus), now),
      customerOrders,
      orderStatuses
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
      .subscribe(OrderCanceled.class, envelope -> saga.on(envelope.data()));

    messageBus
      .bind(shoppingCartsExternal, ShoppingCartsConfig.external, "#")
      .bind(paymentsResponses, PaymentsConfig.responses, responsesForOrders)
      .bind(shipmentsResponses, ShipmentsConfig.responses, responsesForOrders)
      .subscribe(shoppingCartsExternal, ShoppingCartCheckedOut.class, saga::on)
      .subscribe(paymentsResponses, PaymentAuthorized.class, saga::on)
      .subscribe(paymentsResponses, PaymentCaptured.class, saga::on)
      .subscribe(paymentsResponses, PaymentFailed.class, saga::on)
      .subscribe(shipmentsResponses, StockReserved.class, saga::on)
      .subscribe(shipmentsResponses, PackageWasSent.class, saga::on)
      .subscribe(shipmentsResponses, ShipmentFailed.class, saga::on);
  }
}
