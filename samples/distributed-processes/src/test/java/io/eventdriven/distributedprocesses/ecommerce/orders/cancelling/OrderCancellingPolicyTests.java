package io.eventdriven.distributedprocesses.ecommerce.orders.cancelling;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentState;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentState;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderCancellingPolicyTests {
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final UUID productId = UUID.randomUUID();
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime reservedUntil = now.plusHours(2);

  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher sent = new MessageCatcher();
  private final OrderCancellingPolicy policy = new OrderCancellingPolicy(commandBus);

  public OrderCancellingPolicyTests() {
    commandBus.use(sent::catchMessage);

    // Every command the policy can send needs a handler, or the bus refuses it.
    commandBus
      .handle(RecordOrderPaymentFailure.class, _ -> {
      })
      .handle(RecordOrderShipmentFailure.class, _ -> {
      })
      .handle(PaymentCommand.VoidPayment.class, _ -> {
      })
      .handle(ShipmentCommand.ReleaseStock.class, _ -> {
      });
  }

  @Test
  public void aFailedPaymentIsRecordedRatherThanCancellingTheOrderDirectly() {
    policy.on(new PaymentExternalEvent.PaymentFailed(
      orderId.value(), paymentId, totalPrice, now,
      PaymentExternalEvent.PaymentFailed.Reason.TimedOut
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentFailure(orderId, now));
  }

  @Test
  public void anOutOfStockProductIsRecordedRatherThanCancellingTheOrderDirectly() {
    policy.on(new ShipmentExternalEvent.StockReservationFailed(
      shipmentId, orderId.value(), shipmentItems(),
      ShipmentExternalEvent.StockReservationFailed.Reason.ProductsOutOfStock, now
    ));

    sent.shouldReceiveMessages(new RecordOrderShipmentFailure(orderId, now));
  }

  @Test
  public void anExpiredReservationReachesTheOrderSoItIsNeverLeftWaiting() {
    policy.on(new ShipmentExternalEvent.StockReservationFailed(
      shipmentId, orderId.value(), shipmentItems(),
      ShipmentExternalEvent.StockReservationFailed.Reason.ReservationExpired, reservedUntil
    ));

    sent.shouldReceiveMessages(new RecordOrderShipmentFailure(orderId, reservedUntil));
  }

  @Test
  public void aCancelledOrderWithAnAuthorizedHoldVoidsItAndReleasesTheStock() {
    policy.on(orderCancelled(OrderPaymentState.Authorized, OrderShipmentState.Reserved));

    sent.shouldReceiveMessages(
      new PaymentCommand.VoidPayment(paymentId),
      new ShipmentCommand.ReleaseStock(shipmentId)
    );
  }

  @Test
  public void aCancelledOrderWithOnlyTheCardHeldVoidsThatHold() {
    policy.on(orderCancelled(OrderPaymentState.Authorized, OrderShipmentState.NotReserved));

    sent.shouldReceiveMessages(new PaymentCommand.VoidPayment(paymentId));
  }

  @Test
  public void aCancelledOrderThatTookNoHoldsUndoesNothing() {
    policy.on(orderCancelled(OrderPaymentState.NotAuthorized, OrderShipmentState.NotReserved));

    sent.shouldNotReceiveAnyEvent();
  }

  private OrderEvent.OrderCancelled orderCancelled(
    OrderPaymentState paymentState,
    OrderShipmentState shipmentState
  ) {
    return new OrderEvent.OrderCancelled(
      orderId,
      paymentId,
      paymentState,
      shipmentId,
      shipmentState,
      OrderCancellationReason.Requested,
      now
    );
  }

  private ProductItem[] shipmentItems() {
    return new ProductItem[]{new ProductItem(productId, 2)};
  }
}
