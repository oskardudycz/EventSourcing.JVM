package io.eventdriven.distributedprocesses.ecommerce.orders.cancelling;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentState;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentState;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;

import java.util.ArrayList;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

// Compensation
public class OrderCancellingPolicy {
  private final CommandBus commandBus;

  public OrderCancellingPolicy(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  public void on(PaymentExternalEvent.PaymentFailed event) {
    commandBus.send(
      new RecordOrderPaymentFailure(new OrderId(event.referenceId()), event.failedAt())
    );
  }

  public void on(ShipmentExternalEvent.StockReservationFailed event) {
    commandBus.send(
      new RecordOrderShipmentFailure(new OrderId(event.referenceId()), event.failedAt())
    );
  }

  public void on(OrderEvent.OrderCancelled event) {
    var compensations = new ArrayList<>();

    if (event.paymentState() == OrderPaymentState.Authorized) {
      compensations.add(new PaymentCommand.VoidPayment(event.paymentId()));
    }

    if (event.shipmentState() == OrderShipmentState.Reserved) {
      compensations.add(new ShipmentCommand.ReleaseStock(event.shipmentId()));
    }

    commandBus.send(compensations.toArray());
  }
}
