package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ECommerceConfig {
  private ECommerceConfig() {
  }

  public static ECommerceModule configure(
    EventStore eventStore,
    MessageBus messageBus,
    ProductPriceCalculator prices,
    PaymentGateway paymentGateway,
    Duration paymentTimeout,
    Duration authorizationValidity,
    Duration paymentRetryPeriod,
    FulfillmentService fulfillmentService,
    Function<ProductItem, Boolean> isProductAvailable,
    Supplier<OffsetDateTime> now
  ) {
    var shoppingCarts = ShoppingCartsConfig.configure(
      eventStore, messageBus, prices, now
    );
    var orders = OrdersConfig.configure(
      eventStore, messageBus, paymentRetryPeriod, now
    );
    var payments = PaymentsConfig.configure(
      eventStore, messageBus,
      paymentGateway, paymentTimeout, authorizationValidity, now
    );
    var shipments = ShipmentsConfig.configure(
      eventStore, messageBus,
      fulfillmentService, isProductAvailable, now
    );

    OrdersConfig.configureProcess(eventStore, messageBus);

    return new ECommerceModule(shoppingCarts, orders, payments, shipments);
  }
}
