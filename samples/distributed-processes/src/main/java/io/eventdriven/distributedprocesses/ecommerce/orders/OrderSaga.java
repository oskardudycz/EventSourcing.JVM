package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.core.commands.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.DiscardReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartFinalized;
import java.util.Arrays;
import java.util.UUID;

public class OrderSaga {
  private final CommandBus commandBus;

  public OrderSaga(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  // Happy path
  public void on(ShoppingCartFinalized event) {
    commandBus.schedule(new OrderCommand.InitializeOrder(
        event.cartId(), event.clientId(), event.productItems(), event.totalPrice()));
  }

  public void on(OrderInitialized event) {
    commandBus.schedule(
        new PaymentCommand.RequestPayment(UUID.randomUUID(), event.orderId(), event.totalPrice()));
  }

  public void on(PaymentExternalEvent.PaymentFinalized event) {
    commandBus.schedule(
        new RecordOrderPayment(event.orderId(), event.paymentId(), event.finalizedAt()));
  }

  public void on(OrderPaymentRecorded event) {
    commandBus.schedule(new ShipmentCommand.SendPackage(
        event.orderId(),
        Arrays.stream(event.productItems())
            .map(pi -> new ProductItem(pi.productId(), pi.quantity()))
            .toArray(ProductItem[]::new)));
  }

  public void on(ShipmentEvent.PackageWasSent event) {
    commandBus.schedule(new CompleteOrder(event.orderId()));
  }

  // Compensation
  public void on(ShipmentEvent.ProductWasOutOfStock event) {
    commandBus.schedule(
        new CancelOrder(event.orderId(), OrderCancellationReason.ProductWasOutOfStock));
  }

  public void on(OrderCancelled event) {
    if (event.paymentId() == null) {
      return;
    }
    commandBus.schedule(
        new PaymentCommand.DiscardPayment(event.paymentId(), DiscardReason.OrderCancelled));
  }
}
