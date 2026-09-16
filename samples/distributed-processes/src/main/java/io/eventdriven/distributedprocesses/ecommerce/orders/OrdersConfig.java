package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEventForwarder;

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

    var forwarder = new OrderExternalEventForwarder(integrationEventBus);

    internalEventBus
      .subscribe(OrderInitialized.class, forwarder::on)
      .subscribe(OrderConfirmed.class, forwarder::on)
      .subscribe(OrderPaymentCaptured.class, forwarder::on)
      .subscribe(OrderCompleted.class, forwarder::on)
      .subscribe(OrderCancelled.class, forwarder::on);

    return facade;
  }
}
