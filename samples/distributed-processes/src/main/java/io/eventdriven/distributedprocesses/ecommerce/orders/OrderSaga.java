package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderSaga {
  private final MessageBus messageBus;

  public OrderSaga(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  // Hold phase
  public void on(ShoppingCartExternalEvent.ShoppingCartCheckedOut event) {
    messageBus.publish(List.of(
      new PlaceOrder(
        OrderId.derivedFrom(event.cartId().value()),
        event.cartId(),
        event.clientId(),
        toOrderItems(event),
        event.totalPrice()
      )
    ));
  }

  public void on(OrderEvent.OrderPlaced event) {
    var referenceId = event.orderId().value();
    messageBus.publish(List.of(
      new PaymentCommand.AuthorizePayment(PaymentId.derivedFrom(referenceId), referenceId, event.totalPrice()),
      new ShipmentCommand.ReserveStock(referenceId, toShipmentItems(event.productItems()))
    ));
  }

  public void on(PaymentExternalEvent.PaymentAuthorized event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentAuthorization(
        new OrderId(event.referenceId()),
        event.paymentId(),
        event.authorizedAt()
      )
    ));
  }

  public void on(ShipmentExternalEvent.StockReserved event) {
    messageBus.publish(List.of(
      new RecordOrderStockReservation(
        new OrderId(event.referenceId()),
        event.shipmentId(),
        event.reservedAt()
      )
    ));
  }

  // Commit phase
  public void on(OrderEvent.OrderConfirmed event) {
    messageBus.publish(List.of(new ShipmentCommand.RequestFulfillment(event.shipmentId())));
  }

  public void on(ShipmentExternalEvent.PackageWasSent event) {
    messageBus.publish(List.of(
      new RecordOrderPackageSent(
        new OrderId(event.referenceId()),
        event.carrier(),
        event.trackingNumber(),
        event.sentAt()
      )
    ));
  }

  // The shipment does not know the payment. The order does, and names it here.
  public void on(OrderEvent.OrderPaymentDue event) {
    messageBus.publish(List.of(new PaymentCommand.CapturePayment(event.paymentId())));
  }

  public void on(PaymentExternalEvent.PaymentCaptured event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentCapture(new OrderId(event.referenceId()), event.capturedAt())
    ));
  }

  // Compensation
  public void on(PaymentExternalEvent.PaymentFailed event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentFailure(new OrderId(event.referenceId()), event.failedAt())
    ));
  }

  public void on(ShipmentExternalEvent.ShipmentFailed event) {
    var reason = switch (event.reason()) {
      case ProductsOutOfStock -> OrderCancellationReason.ProductWasOutOfStock;
      case FulfillmentRejected -> OrderCancellationReason.CouldNotShip;
    };

    messageBus.publish(List.of(
      new RecordOrderShipmentFailure(new OrderId(event.referenceId()), reason, event.failedAt())
    ));
  }

  // Paying again
  public void on(OrderEvent.OrderPaymentRetried event) {
    messageBus.publish(List.of(
      new PaymentCommand.AuthorizePayment(event.paymentId(), event.orderId().value(), event.amount())
    ));
  }

  public void on(OrderEvent.OrderCancelled event) {
    var compensations = new ArrayList<>();
    if (event.paymentStatus() == OrderPaymentStatus.Authorized) {
      compensations.add(new PaymentCommand.VoidPayment(event.paymentId()));
    }
    if (event.shipmentStatus() == OrderShipmentStatus.Reserved) {
      compensations.add(new ShipmentCommand.ReleaseStock(event.shipmentId()));
    }
    messageBus.publish(compensations);
  }

  private static PricedProductItem[] toOrderItems(ShoppingCartExternalEvent.ShoppingCartCheckedOut event) {
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
