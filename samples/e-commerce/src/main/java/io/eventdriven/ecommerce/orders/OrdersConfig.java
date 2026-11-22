package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.core.commands.CommandHandler;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.ecommerce.orders.getcustomerorders.CustomerOrderRepository;
import io.eventdriven.ecommerce.orders.getcustomerorders.CustomerOrders;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatusRepository;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatuses;
import io.eventdriven.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.eventdriven.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.ecommerce.orders.unpaid.UnpaidOrdersWorker;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentAuthorized;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentCaptured;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasSent;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.ShipmentFailed;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.StockReserved;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrdersConfig {
    public static final String internal = "orders-internal";
    public static final String shoppingCartsExternal = "orders-shopping_carts-external";
    public static final String paymentsResponses = "orders-payments-responses";
    public static final String shipmentsResponses = "orders-shipments-responses";
    public static final String responsesForOrders = "ecommerce.order.*";

    @Bean("ordersMapping")
    public static MessageMapping mapping() {
        return new MessageMapping()
                .trust(
                        OrdersConfig.class.getPackageName(),
                        ShoppingCartExternalEvent.class.getPackageName(),
                        PaymentExternalEvent.class.getPackageName(),
                        ShipmentExternalEvent.class.getPackageName());
    }

    @Bean
    public static MessageRoutes ordersRoutes() {
        return new MessageRoutes()
                .routeTarget(MessageRoutes.inPackage(OrdersConfig.class.getPackageName()), internal)
                .routeKey(
                        OrderCommand.class,
                        command -> Urns.routingKeyOf(command.orderId().value()));
    }

    @Bean
    public static MessageChannel ordersInternalChannel() {
        return new MessageChannel(internal);
    }

    @Bean
    public static MessageQueue ordersInternalQueue() {
        return new MessageQueue(internal, internal, "#");
    }

    @Bean
    public static MessageQueue ordersShoppingCartsExternalQueue() {
        return new MessageQueue(shoppingCartsExternal, ShoppingCartsConfig.external, "#");
    }

    @Bean
    public static MessageQueue ordersPaymentsResponsesQueue() {
        return new MessageQueue(paymentsResponses, PaymentsConfig.responses, responsesForOrders);
    }

    @Bean
    public static MessageQueue ordersShipmentsResponsesQueue() {
        return new MessageQueue(shipmentsResponses, ShipmentsConfig.responses, responsesForOrders);
    }

    @Bean
    public OrderFacade.PaymentTerms paymentTerms() {
        return OrderFacade.PaymentTerms.within(Duration.ofDays(7));
    }

    @Bean
    public OrderFacade orderFacade(
            EventStore eventStore,
            MessageContext messageContext,
            MessageBus messageBus,
            OrderFacade.PaymentTerms paymentTerms,
            Supplier<OffsetDateTime> now) {
        var commandHandler = new CommandHandler<Order.Id, Order, OrderEvent>(
                eventStore, messageContext, OrderFacade::mapToStreamId, Order::initial, Order::evolve);

        var facade = new OrderFacade(commandHandler, paymentTerms, now);

        messageBus
                .subscribe(internal, PlaceOrder.class, facade::placeOrder)
                .subscribe(internal, RecordOrderPaymentAuthorization.class, facade::recordOrderPaymentAuthorization)
                .subscribe(internal, RecordOrderStockReservation.class, facade::recordOrderStockReservation)
                .subscribe(internal, RecordOrderPackageSent.class, facade::recordOrderPackageSent)
                .subscribe(internal, RecordOrderPaymentCapture.class, facade::recordOrderPaymentCapture)
                .subscribe(internal, RecordOrderPaymentFailure.class, facade::recordOrderPaymentFailure)
                .subscribe(internal, RecordOrderShipmentFailure.class, facade::recordOrderShipmentFailure)
                .subscribe(internal, CloseUnpaidOrder.class, facade::closeUnpaidOrder);

        return facade;
    }

    @Bean
    public PendingOrders pendingOrders(EventStore eventStore, PendingOrderRepository pendingOrderRepository) {
        var pendingOrders = new PendingOrders(pendingOrderRepository);

        eventStore.subscribe("pending-orders", pendingOrders.canHandle(), pendingOrders::handle);

        return pendingOrders;
    }

    @Bean
    public OrdersAwaitingPayment ordersAwaitingPayment(
            EventStore eventStore, OrderAwaitingPaymentRepository orderAwaitingPaymentRepository) {
        var ordersAwaitingPayment = new OrdersAwaitingPayment(orderAwaitingPaymentRepository);

        eventStore.subscribe(
                "orders-awaiting-payment", ordersAwaitingPayment.canHandle(), ordersAwaitingPayment::handle);

        return ordersAwaitingPayment;
    }

    @Bean
    public CustomerOrders customerOrders(EventStore eventStore, CustomerOrderRepository customerOrderRepository) {
        var customerOrders = new CustomerOrders(customerOrderRepository);

        eventStore.subscribe("customer-orders", customerOrders.canHandle(), customerOrders::handle);

        return customerOrders;
    }

    @Bean
    public OrderStatuses orderStatuses(EventStore eventStore, OrderStatusRepository orderStatusRepository) {
        var orderStatuses = new OrderStatuses(orderStatusRepository);

        eventStore.subscribe("order-statuses", orderStatuses.canHandle(), orderStatuses::handle);

        return orderStatuses;
    }

    @Bean
    public ScheduledUnpaidOrdersWorker unpaidOrdersWorker(
            OrdersAwaitingPayment ordersAwaitingPayment, MessageBus messageBus, Supplier<OffsetDateTime> now) {
        return new ScheduledUnpaidOrdersWorker(new UnpaidOrdersWorker(ordersAwaitingPayment, messageBus), now);
    }

    @Bean
    public SmartInitializingSingleton orderSagaSubscriptions(EventStore eventStore, MessageBus messageBus) {
        return () -> subscribeOrderSaga(eventStore, messageBus);
    }

    public void subscribeOrderSaga(EventStore eventStore, MessageBus messageBus) {
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
                .subscribe(shoppingCartsExternal, ShoppingCartCheckedOut.class, saga::onShoppingCartCheckedOut)
                .subscribe(paymentsResponses, PaymentAuthorized.class, saga::onPaymentAuthorized)
                .subscribe(paymentsResponses, PaymentCaptured.class, saga::onPaymentCaptured)
                .subscribe(paymentsResponses, PaymentFailed.class, saga::onPaymentFailed)
                .subscribe(shipmentsResponses, StockReserved.class, saga::onStockReserved)
                .subscribe(shipmentsResponses, PackageWasSent.class, saga::onPackageWasSent)
                .subscribe(shipmentsResponses, ShipmentFailed.class, saga::onShipmentFailed);
    }
}
