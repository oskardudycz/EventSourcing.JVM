package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.delivery.DeliveryProvider;
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
    CommandBus commandBus,
    EventStore eventStore,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus,
    ProductPriceCalculator prices,
    PaymentGateway paymentGateway,
    Duration paymentTimeout,
    Duration authorizationValidity,
    DeliveryProvider deliveryProvider,
    Function<ProductItem, Boolean> isProductAvailable,
    Duration reservationValidity,
    Supplier<OffsetDateTime> now
  ) {
    var shoppingCarts = ShoppingCartsConfig.configure(
      commandBus, eventStore, internalEventBus, integrationEventBus, prices, now
    );
    var orders = OrdersConfig.configure(
      commandBus, eventStore, internalEventBus, integrationEventBus, now
    );
    var payments = PaymentsConfig.configure(
      commandBus, eventStore, internalEventBus, integrationEventBus,
      paymentGateway, paymentTimeout, authorizationValidity, now
    );
    var shipments = ShipmentsConfig.configure(
      commandBus, eventStore, internalEventBus, integrationEventBus,
      deliveryProvider, isProductAvailable, reservationValidity, now
    );

    OrdersConfig.configureProcess(commandBus, internalEventBus, integrationEventBus);

    return new ECommerceModule(shoppingCarts, orders, payments, shipments);
  }
}
