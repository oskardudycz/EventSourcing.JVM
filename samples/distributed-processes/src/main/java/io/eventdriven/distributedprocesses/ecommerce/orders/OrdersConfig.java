package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.UnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentAuthorized;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentCaptured;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.ShipmentFailed;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent.StockReserved;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

public final class OrdersConfig {
    public static final String internal = "orders-internal";
    public static final String shoppingCartsExternal = "orders-shopping_carts-external";
    public static final String paymentsResponses = "orders-payments-responses";
    public static final String shipmentsResponses = "orders-shipments-responses";
    public static final String responsesForOrders = "ecommerce.order.*";

    private OrdersConfig() {}

    public static OrdersModule configure(
            EventStore eventStore,
            MessageBus messageBus,
            PaymentTerms paymentTerms,
            PendingOrderRepository pendingOrderRepository,
            OrderAwaitingPaymentRepository orderAwaitingPaymentRepository,
            CustomerOrderRepository customerOrderRepository,
            OrderStatusRepository orderStatusRepository,
            Supplier<OffsetDateTime> now) {
        var store =
                new AggregateStore<Order, OrderEvent, OrderId>(eventStore, OrderFacade::mapToStreamId, Order::empty);

        var facade = new OrderFacade(store, paymentTerms, now);

        messageBus
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(OrdersConfig.class.getPackageName()), internal)
                        .routeKey(
                                OrderCommand.class,
                                command -> Urns.routingKeyOf(command.orderId().value())))
                .channel(internal)
                .bind(internal, internal, "#")
                .subscribe(internal, PlaceOrder.class, facade::placeOrder)
                .subscribe(internal, RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
                .subscribe(internal, RecordOrderStockReservation.class, facade::recordOrderStockReservation)
                .subscribe(internal, RecordOrderPackageSent.class, facade::recordOrderPackageSent)
                .subscribe(internal, RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
                .subscribe(internal, RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
                .subscribe(internal, RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
                .subscribe(internal, CloseUnpaidOrder.class, facade::closeUnpaidOrder);

        var pendingOrders = new PendingOrders(pendingOrderRepository);
        var ordersAwaitingPayment = new OrdersAwaitingPayment(orderAwaitingPaymentRepository);

        eventStore.subscribe("pending-orders", pendingOrders.canHandle(), pendingOrders::handle);

        eventStore.subscribe(
                "orders-awaiting-payment", ordersAwaitingPayment.canHandle(), ordersAwaitingPayment::handle);

        var customerOrders = new CustomerOrders(customerOrderRepository);
        var orderStatuses = new OrderStatuses(orderStatusRepository);

        eventStore.subscribe("customer-orders", customerOrders.canHandle(), customerOrders::handle);

        eventStore.subscribe("order-statuses", orderStatuses.canHandle(), orderStatuses::handle);

        return new OrdersModule(
                facade,
                pendingOrders,
                new ScheduledUnpaidOrdersWorker(new UnpaidOrdersWorker(ordersAwaitingPayment, messageBus), now),
                customerOrders,
                orderStatuses);
    }

    public static void configureProcess(EventStore eventStore, MessageBus messageBus) {
        var saga = new OrderSaga(messageBus);

        eventStore.subscribe(
                "order-saga",
                List.of(
                        OrderPlaced.class,
                        OrderConfirmed.class,
                        OrderPaymentDue.class,
                        OrderPaymentRetried.class,
                        OrderCanceled.class),
                envelope -> {
                    switch (envelope.data()) {
                        case OrderPlaced event -> saga.onOrderPlaced(event);
                        case OrderConfirmed event -> saga.onOrderConfirmed(event);
                        case OrderPaymentDue event -> saga.onOrderPaymentDue(event);
                        case OrderPaymentRetried event -> saga.onOrderPaymentRetried(event);
                        case OrderCanceled event -> saga.onOrderCanceled(event);
                        default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
                    }
                });

        messageBus
                .bind(shoppingCartsExternal, ShoppingCartsConfig.external, "#")
                .bind(paymentsResponses, PaymentsConfig.responses, responsesForOrders)
                .bind(shipmentsResponses, ShipmentsConfig.responses, responsesForOrders)
                .subscribe(shoppingCartsExternal, ShoppingCartCheckedOut.class, saga::onShoppingCartCheckedOut)
                .subscribe(paymentsResponses, PaymentAuthorized.class, saga::onPaymentAuthorized)
                .subscribe(paymentsResponses, PaymentCaptured.class, saga::onPaymentCaptured)
                .subscribe(paymentsResponses, PaymentFailed.class, saga::onPaymentFailed)
                .subscribe(shipmentsResponses, StockReserved.class, saga::onStockReserved)
                .subscribe(shipmentsResponses, PackageWasSent.class, saga::onPackageWasSent)
                .subscribe(shipmentsResponses, ShipmentFailed.class, saga::onShipmentFailed);
    }
}
