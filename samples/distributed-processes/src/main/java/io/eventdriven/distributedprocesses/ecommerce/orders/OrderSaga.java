package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartFinalized;

import java.util.Arrays;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderSaga {
  private final CommandBus commandBus;

  public OrderSaga(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  // Hold phase — both holds are reversible, and both expire on their own
  public void on(ShoppingCartFinalized event) {
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

  public void on(OrderExternalEvent.OrderInitialized event) {
    var referenceId = event.orderId().value();

    commandBus.send(
      new PaymentCommand.AuthorizePayment(referenceId, event.totalPrice())
    );
    commandBus.send(
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

  // Commit phase — the goods leave, and the money moves as they go
  public void on(OrderExternalEvent.OrderConfirmed event) {
    commandBus.send(new ShipmentCommand.SendPackage(event.shipmentId()));
  }

  public void on(ShipmentExternalEvent.PackageWasSent event) {
    commandBus.send(
      new RecordOrderPackageSent(new OrderId(event.referenceId()), event.sentAt())
    );
  }

  // The dispatch itself does not know the payment. The order does, and republishes it here.
  public void on(OrderExternalEvent.OrderPackageSent event) {
    commandBus.send(new PaymentCommand.CapturePayment(event.paymentId()));
  }

  public void on(PaymentExternalEvent.PaymentCaptured event) {
    commandBus.send(
      new RecordOrderPaymentCapture(new OrderId(event.referenceId()), event.capturedAt())
    );
  }

  public void on(ShipmentExternalEvent.PackageWasDelivered event) {
    commandBus.send(
      new RecordOrderDelivery(new OrderId(event.referenceId()), event.deliveredAt())
    );
  }

  // Compensation
  public void on(PaymentExternalEvent.PaymentFailed event) {
    commandBus.send(
      new RecordOrderPaymentFailure(new OrderId(event.referenceId()), event.failedAt())
    );
  }

  public void on(ShipmentExternalEvent.ProductWasOutOfStock event) {
    commandBus.send(
      new RecordOrderShipmentFailure(
        new OrderId(event.referenceId()),
        event.availabilityCheckedAt()
      )
    );
  }

  public void on(ShipmentExternalEvent.StockReservationExpired event) {
    commandBus.send(
      new RecordOrderShipmentFailure(new OrderId(event.referenceId()), event.expiredAt())
    );
  }

  public void on(OrderExternalEvent.OrderCancelled event) {
    switch (event.paymentState()) {
      case Authorized -> commandBus.send(new PaymentCommand.VoidPayment(event.paymentId()));
      case Captured -> commandBus.send(new PaymentCommand.RefundPayment(event.paymentId()));
      case NotAuthorized -> {
      }
    }

    if (event.shipmentState() == OrderShipmentState.Reserved) {
      commandBus.send(new ShipmentCommand.ReleaseStock(event.shipmentId()));
    }
  }

  private static PricedProductItem[] toOrderItems(ShoppingCartFinalized event) {
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
