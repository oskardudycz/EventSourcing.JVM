package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsModule;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsModule;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;

public record ECommerceModule(
  ShoppingCartFacade shoppingCarts,
  OrderFacade orders,
  PaymentsModule payments,
  ShipmentsModule shipments
) {
}
