package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.PaymentTerms;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

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
    PaymentValidity paymentValidity,
    PaymentTerms paymentTerms,
    FulfillmentService fulfillmentService,
    Function<ProductItem, Boolean> isProductAvailable,
    Supplier<OffsetDateTime> now
  ) {
    var shoppingCarts = ShoppingCartsConfig.configure(
      eventStore, messageBus, prices, now
    );
    var orders = OrdersConfig.configure(
      eventStore, messageBus, paymentTerms, now
    );
    var payments = PaymentsConfig.configure(
      eventStore, messageBus,
      paymentGateway, paymentValidity, now
    );
    var shipments = ShipmentsConfig.configure(
      eventStore, messageBus,
      fulfillmentService, isProductAvailable, now
    );

    OrdersConfig.configureProcess(eventStore, messageBus);

    return new ECommerceModule(shoppingCarts, orders, payments, shipments);
  }
}
