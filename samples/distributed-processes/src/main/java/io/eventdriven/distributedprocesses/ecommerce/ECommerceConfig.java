package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.PaymentTerms;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ECommerceConfig {
    private ECommerceConfig() {}

    public static ECommerceModule configure(
            EventStore eventStore,
            MessageBus messageBus,
            ProductPriceCalculator prices,
            PaymentGateway paymentGateway,
            PaymentValidity paymentValidity,
            PaymentTerms paymentTerms,
            FulfillmentService fulfillmentService,
            Function<ProductItem, Boolean> isProductAvailable,
            CustomerShoppingCartRepository customerShoppingCartRepository,
            ShoppingCartSummaryRepository shoppingCartSummaryRepository,
            CartRepository cartRepository,
            PendingOrderRepository pendingOrderRepository,
            OrderAwaitingPaymentRepository orderAwaitingPaymentRepository,
            CustomerOrderRepository customerOrderRepository,
            OrderStatusRepository orderStatusRepository,
            PaymentSummaryRepository paymentSummaryRepository,
            PendingPaymentRepository pendingPaymentRepository,
            AuthorizedPaymentRepository authorizedPaymentRepository,
            Supplier<OffsetDateTime> now) {
        var shoppingCarts = ShoppingCartsConfig.configure(
                eventStore,
                messageBus,
                prices,
                customerShoppingCartRepository,
                shoppingCartSummaryRepository,
                cartRepository,
                now);
        var orders = OrdersConfig.configure(
                eventStore,
                messageBus,
                paymentTerms,
                pendingOrderRepository,
                orderAwaitingPaymentRepository,
                customerOrderRepository,
                orderStatusRepository,
                now);
        var payments = PaymentsConfig.configure(
                eventStore,
                messageBus,
                paymentGateway,
                paymentValidity,
                paymentSummaryRepository,
                pendingPaymentRepository,
                authorizedPaymentRepository,
                now);
        var shipments = ShipmentsConfig.configure(eventStore, messageBus, fulfillmentService, isProductAvailable, now);

        OrdersConfig.configureProcess(eventStore, messageBus);

        return new ECommerceModule(shoppingCarts, orders, payments, shipments);
    }
}
