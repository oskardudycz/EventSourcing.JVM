package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
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
  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.shoppingCartsExternal, description = "Starts an order for the checked-out shopping cart."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = PlaceOrder.class, description = "Places an order for the checked-out shopping cart."))
  public void onShoppingCartCheckedOut(ShoppingCartExternalEvent.ShoppingCartCheckedOut event) {
    messageBus.publish(List.of(
      new PlaceOrder(
        OrderId.derivedFrom(event.cartId().value()),
        event.cartId(),
        event.customerId(),
        toOrderItems(event),
        event.totalPrice()
      )
    ));
  }

  @AsyncPublisher(operation = @AsyncOperation(channelName = PaymentsConfig.requests, payloadType = PaymentCommand.AuthorizePayment.class, description = "Asks for the order's payment when the order is placed, and again when the customer retries a failed payment."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.requests, payloadType = ShipmentCommand.ReserveStock.class, description = "Asks the warehouse to set aside the order's products when the order is placed."))
  public void onOrderPlaced(OrderEvent.OrderPlaced event) {
    var referenceId = event.orderId().value();
    messageBus.publish(List.of(
      new PaymentCommand.AuthorizePayment(PaymentId.derivedFrom(referenceId), referenceId, event.totalPrice()),
      new ShipmentCommand.ReserveStock(referenceId, toShipmentItems(event.productItems()))
    ));
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.paymentsResponses, description = "Passes the authorized payment on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderPaymentAuthorization.class, description = "Records on the order that the payment is authorized."))
  public void onPaymentAuthorized(PaymentExternalEvent.PaymentAuthorized event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentAuthorization(
        new OrderId(event.referenceId()),
        event.paymentId(),
        event.authorizedAt()
      )
    ));
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.shipmentsResponses, description = "Passes the products that are set aside on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderStockReservation.class, description = "Records on the order that the products are set aside."))
  public void onStockReserved(ShipmentExternalEvent.StockReserved event) {
    messageBus.publish(List.of(
      new RecordOrderStockReservation(
        new OrderId(event.referenceId()),
        event.shipmentId(),
        event.reservedAt()
      )
    ));
  }

  // Commit phase
  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.requests, payloadType = ShipmentCommand.RequestFulfillment.class, description = "Asks the warehouse to send the products when the order is confirmed."))
  public void onOrderConfirmed(OrderEvent.OrderConfirmed event) {
    messageBus.publish(List.of(new ShipmentCommand.RequestFulfillment(event.shipmentId())));
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.shipmentsResponses, description = "Passes the sent package on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderPackageSent.class, description = "Records on the order that the package was sent."))
  public void onPackageWasSent(ShipmentExternalEvent.PackageWasSent event) {
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
  @AsyncPublisher(operation = @AsyncOperation(channelName = PaymentsConfig.requests, payloadType = PaymentCommand.CapturePayment.class, description = "Asks to charge the customer when the package was sent."))
  public void onOrderPaymentDue(OrderEvent.OrderPaymentDue event) {
    messageBus.publish(List.of(new PaymentCommand.CapturePayment(event.paymentId())));
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.paymentsResponses, description = "Passes the charge on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderPaymentCapture.class, description = "Records on the order that the customer was charged."))
  public void onPaymentCaptured(PaymentExternalEvent.PaymentCaptured event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentCapture(new OrderId(event.referenceId()), event.capturedAt())
    ));
  }

  // Compensation
  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.paymentsResponses, description = "Passes the failed payment on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderPaymentFailure.class, description = "Records on the order that the payment failed."))
  public void onPaymentFailed(PaymentExternalEvent.PaymentFailed event) {
    messageBus.publish(List.of(
      new RecordOrderPaymentFailure(new OrderId(event.referenceId()), event.failedAt())
    ));
  }

  @AsyncListener(operation = @AsyncOperation(channelName = OrdersConfig.shipmentsResponses, description = "Passes the failed shipment on to the order."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = OrdersConfig.internal, payloadType = RecordOrderShipmentFailure.class, description = "Records on the order that the products cannot ship."))
  public void onShipmentFailed(ShipmentExternalEvent.ShipmentFailed event) {
    var reason = switch (event.reason()) {
      case ProductsOutOfStock -> OrderCancellationReason.ProductWasOutOfStock;
      case FulfillmentRejected -> OrderCancellationReason.CouldNotShip;
    };

    messageBus.publish(List.of(
      new RecordOrderShipmentFailure(new OrderId(event.referenceId()), reason, event.failedAt())
    ));
  }

  // Paying again
  @AsyncPublisher(operation = @AsyncOperation(channelName = PaymentsConfig.requests, payloadType = PaymentCommand.AuthorizePayment.class, description = "Asks for the order's payment when the order is placed, and again when the customer retries a failed payment."))
  public void onOrderPaymentRetried(OrderEvent.OrderPaymentRetried event) {
    messageBus.publish(List.of(
      new PaymentCommand.AuthorizePayment(event.paymentId(), event.orderId().value(), event.amount())
    ));
  }

  @AsyncPublisher(operation = @AsyncOperation(channelName = PaymentsConfig.requests, payloadType = PaymentCommand.CancelPayment.class, description = "Cancels the authorized payment when the order is canceled."))
  @AsyncPublisher(operation = @AsyncOperation(channelName = ShipmentsConfig.requests, payloadType = ShipmentCommand.ReleaseStock.class, description = "Puts the products back on sale when the order is canceled."))
  public void onOrderCanceled(OrderEvent.OrderCanceled event) {
    var compensations = new ArrayList<>();
    if (event.paymentStatus() == OrderPaymentStatus.Authorized) {
      compensations.add(new PaymentCommand.CancelPayment(event.paymentId()));
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
