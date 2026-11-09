package io.eventdriven.distributedprocesses.ecommerce.orders.fulfilling;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

// Commit phase
public class OrderFulfillingPolicy {
  private final CommandBus commandBus;

  public OrderFulfillingPolicy(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  public void on(OrderEvent.OrderConfirmed event) {
    commandBus.send(new ShipmentCommand.SendPackage(event.shipmentId()));
  }

  public void on(ShipmentExternalEvent.PackageWasSent event) {
    commandBus.send(
      new RecordOrderPackageSent(
        new OrderId(event.referenceId()),
        event.carrier(),
        event.trackingNumber(),
        event.sentAt()
      )
    );
  }

  // The shipment does not know the payment. The order does, and names it here.
  public void on(OrderEvent.OrderPackageSent event) {
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
}
