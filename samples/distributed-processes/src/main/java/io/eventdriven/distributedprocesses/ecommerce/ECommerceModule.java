package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersModule;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsModule;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsModule;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;

public record ECommerceModule(
  ShoppingCartFacade shoppingCarts,
  OrdersModule orders,
  PaymentsModule payments,
  ShipmentsModule shipments
) {
}
