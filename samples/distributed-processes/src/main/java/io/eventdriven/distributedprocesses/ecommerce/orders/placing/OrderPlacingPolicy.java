package io.eventdriven.distributedprocesses.ecommerce.orders.placing;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;

import java.util.Arrays;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

// Hold phase
public class OrderPlacingPolicy {
  private final CommandBus commandBus;

  public OrderPlacingPolicy(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  public void on(ShoppingCartExternalEvent.ShoppingCartFinalized event) {
    commandBus.send(
      new InitializeOrder(
        OrderId.derivedFrom(event.cartId().value()),
        event.cartId(),
        event.clientId(),
        toOrderItems(event),
        event.totalPrice()
      )
    );
  }

  public void on(OrderEvent.OrderInitialized event) {
    var referenceId = event.orderId().value();

    commandBus.send(
      new PaymentCommand.AuthorizePayment(referenceId, event.totalPrice()),
      new ShipmentCommand.ReserveStock(referenceId, toShipmentItems(event.productItems()))
    );
  }

  public void on(PaymentExternalEvent.PaymentAuthorized event) {
    commandBus.send(
      new RecordOrderPaymentAuthorization(
        new OrderId(event.referenceId()),
        event.paymentId(),
        event.authorizedAt()
      )
    );
  }

  public void on(ShipmentExternalEvent.StockReserved event) {
    commandBus.send(
      new RecordOrderStockReservation(
        new OrderId(event.referenceId()),
        event.shipmentId(),
        event.reservedAt()
      )
    );
  }

  private static PricedProductItem[] toOrderItems(ShoppingCartExternalEvent.ShoppingCartFinalized event) {
    return Arrays.stream(event.productItems())
      .map(pi -> new PricedProductItem(pi.productId(), pi.quantity(), pi.unitPrice()))
      .toArray(PricedProductItem[]::new);
  }

  private static ProductItem[] toShipmentItems(PricedProductItem[] productItems) {
    return Arrays.stream(productItems)
      .map(pi -> new ProductItem(pi.productId(), pi.quantity()))
      .toArray(ProductItem[]::new);
  }
}
