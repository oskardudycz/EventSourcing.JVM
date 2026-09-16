package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartFinalized;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderSagaTests {
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final OrderId orderId = OrderId.derivedFrom(cartId.value());
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final UUID clientId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plusDays(7);
  private final OffsetDateTime reservedUntil = now.plusHours(2);

  private final PricedProductItem[] orderItems = new PricedProductItem[]{
    new PricedProductItem(productId, 2, 12.5)
  };

  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher sent = new MessageCatcher();
  private final OrderSaga saga = new OrderSaga(commandBus);

  public OrderSagaTests() {
    commandBus.use(sent::catchMessage);

    // Every command the saga can send needs a handler, or the bus refuses it.
    commandBus
      .handle(InitializeOrder.class, _ -> {
      })
      .handle(RecordOrderPaymentAuthorization.class, _ -> {
      })
      .handle(RecordOrderStockReservation.class, _ -> {
      })
      .handle(RecordOrderPackageSent.class, _ -> {
      })
      .handle(RecordOrderPaymentCapture.class, _ -> {
      })
      .handle(RecordOrderDelivery.class, _ -> {
      })
      .handle(RecordOrderPaymentFailure.class, _ -> {
      })
      .handle(RecordOrderShipmentFailure.class, _ -> {
      })
      .handle(PaymentCommand.AuthorizePayment.class, _ -> {
      })
      .handle(PaymentCommand.CapturePayment.class, _ -> {
      })
      .handle(PaymentCommand.VoidPayment.class, _ -> {
      })
      .handle(PaymentCommand.RefundPayment.class, _ -> {
      })
      .handle(ShipmentCommand.ReserveStock.class, _ -> {
      })
      .handle(ShipmentCommand.SendPackage.class, _ -> {
      })
      .handle(ShipmentCommand.ReleaseStock.class, _ -> {
      });
  }

  @Test
  public void aFinalizedCartStartsAnOrderWhoseIdIsDerivedFromTheCart() {
    saga.on(new ShoppingCartFinalized(cartId, clientId, cartItems(), totalPrice, now));

    sent.shouldReceiveMessages(
      new InitializeOrder(orderId, cartId, clientId, orderItems, totalPrice)
    );
  }

  @Test
  public void anInitializedOrderTakesBothHoldsTogether() {
    saga.on(orderInitialized());

    sent.shouldReceiveMessages(
      new PaymentCommand.AuthorizePayment(orderId.value(), totalPrice),
      new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems())
    );
  }

  @Test
  public void anAuthorizedPaymentIsRecordedAgainstTheOrderTheReferenceNames() {
    saga.on(new PaymentExternalEvent.PaymentAuthorized(
      orderId.value(), paymentId, totalPrice, now, expiresAt
    ));

    sent.shouldReceiveMessages(
      new RecordOrderPaymentAuthorization(orderId, paymentId, now)
    );
  }

  @Test
  public void reservedStockIsRecordedAgainstTheOrder() {
    saga.on(new ShipmentExternalEvent.StockReserved(
      shipmentId, orderId.value(), now, reservedUntil
    ));

    sent.shouldReceiveMessages(new RecordOrderStockReservation(orderId, shipmentId, now));
  }

  @Test
  public void aConfirmedOrderSendsThePackageItAlreadyReserved() {
    saga.on(new OrderExternalEvent.OrderConfirmed(orderId, shipmentId, now));

    sent.shouldReceiveMessages(new ShipmentCommand.SendPackage(shipmentId));
  }

  @Test
  public void aSentPackageIsRecordedAgainstTheOrder() {
    saga.on(new ShipmentExternalEvent.PackageWasSent(
      shipmentId, orderId.value(), shipmentItems(), now
    ));

    sent.shouldReceiveMessages(new RecordOrderPackageSent(orderId, now));
  }

  @Test
  public void theOrdersOwnDispatchEventIsWhatTriggersTheCapture() {
    saga.on(new OrderExternalEvent.OrderPackageSent(orderId, paymentId, now));

    sent.shouldReceiveMessages(new PaymentCommand.CapturePayment(paymentId));
  }

  @Test
  public void aCapturedPaymentIsRecordedAgainstTheOrder() {
    saga.on(new PaymentExternalEvent.PaymentCaptured(
      orderId.value(), paymentId, totalPrice, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentCapture(orderId, now));
  }

  @Test
  public void aDeliveredPackageIsRecordedAgainstTheOrder() {
    saga.on(new ShipmentExternalEvent.PackageWasDelivered(shipmentId, orderId.value(), now));

    sent.shouldReceiveMessages(new RecordOrderDelivery(orderId, now));
  }

  @Test
  public void aFailedPaymentIsRecordedRatherThanCancellingTheOrderDirectly() {
    saga.on(new PaymentExternalEvent.PaymentFailed(
      orderId.value(), paymentId, totalPrice, now,
      PaymentExternalEvent.PaymentFailed.Reason.TimedOut
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentFailure(orderId, now));
  }

  @Test
  public void anOutOfStockProductIsRecordedRatherThanCancellingTheOrderDirectly() {
    saga.on(new ShipmentExternalEvent.ProductWasOutOfStock(
      shipmentId, orderId.value(), shipmentItems(), now
    ));

    sent.shouldReceiveMessages(new RecordOrderShipmentFailure(orderId, now));
  }

  @Test
  public void anExpiredReservationReachesTheOrderSoItIsNeverLeftWaiting() {
    saga.on(new ShipmentExternalEvent.StockReservationExpired(
      shipmentId, orderId.value(), reservedUntil
    ));

    sent.shouldReceiveMessages(new RecordOrderShipmentFailure(orderId, reservedUntil));
  }

  @Test
  public void aCancelledOrderWithAnAuthorizedHoldVoidsItAndReleasesTheStock() {
    saga.on(orderCancelled(OrderPaymentState.Authorized, OrderShipmentState.Reserved));

    sent.shouldReceiveMessages(
      new PaymentCommand.VoidPayment(paymentId),
      new ShipmentCommand.ReleaseStock(shipmentId)
    );
  }

  @Test
  public void aCancelledOrderWhoseMoneyAlreadyMovedIsRefundedInstead() {
    saga.on(orderCancelled(OrderPaymentState.Captured, OrderShipmentState.Sent));

    sent.shouldReceiveMessages(new PaymentCommand.RefundPayment(paymentId));
  }

  @Test
  public void aCancelledOrderThatTookNoHoldsUndoesNothing() {
    saga.on(orderCancelled(OrderPaymentState.NotAuthorized, OrderShipmentState.NotReserved));

    sent.shouldNotReceiveAnyEvent();
  }

  @Test
  public void handlingTheSameEventTwiceSendsTwoIdenticalCommands() {
    saga.on(orderInitialized());
    saga.on(orderInitialized());

    var authorizePayment = new PaymentCommand.AuthorizePayment(orderId.value(), totalPrice);
    var reserveStock = new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems());

    sent.shouldReceiveMessages(authorizePayment, reserveStock, authorizePayment, reserveStock);
  }

  private OrderExternalEvent.OrderInitialized orderInitialized() {
    return new OrderExternalEvent.OrderInitialized(
      orderId, cartId, clientId, orderItems, totalPrice, now
    );
  }

  private OrderExternalEvent.OrderCancelled orderCancelled(
    OrderPaymentState paymentState,
    OrderShipmentState shipmentState
  ) {
    return new OrderExternalEvent.OrderCancelled(
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

  private io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem[] cartItems() {
    return new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem[]{
      new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem(
        new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem(productId, 2),
        12.5
      )
    };
  }
}
