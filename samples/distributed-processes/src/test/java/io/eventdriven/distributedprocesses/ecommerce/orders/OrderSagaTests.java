package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
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
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";
  private final OffsetDateTime now = OffsetDateTime.now();

  private final PricedProductItem[] orderItems = new PricedProductItem[]{
    new PricedProductItem(productId, 2, 12.5)
  };

  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher sent = new MessageCatcher();
  private final OrderSaga saga = new OrderSaga(messageBus);

  public OrderSagaTests() {
    messageBus.use(sent::catchMessage);
  }

  // Hold phase

  @Test
  public void aFinalizedCartStartsAnOrderWhoseIdIsDerivedFromTheCart() {
    saga.on(new ShoppingCartExternalEvent.ShoppingCartFinalized(cartId, clientId, cartItems(), totalPrice, now));

    sent.shouldReceiveMessages(
      new PlaceOrder(orderId, cartId, clientId, orderItems, totalPrice)
    );
  }

  @Test
  public void aPlacedOrderTakesBothHoldsTogether() {
    saga.on(orderPlaced());

    sent.shouldReceiveMessages(
      new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), totalPrice),
      new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems())
    );
  }

  @Test
  public void anAuthorizedPaymentIsRecordedAgainstTheOrderTheReferenceNames() {
    saga.on(new PaymentExternalEvent.PaymentAuthorized(
      orderId.value(), paymentId, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentAuthorization(orderId, paymentId, now));
  }

  @Test
  public void reservedStockIsRecordedAgainstTheOrder() {
    saga.on(new ShipmentExternalEvent.StockReserved(
      shipmentId, orderId.value(), now
    ));

    sent.shouldReceiveMessages(new RecordOrderStockReservation(orderId, shipmentId, now));
  }

  @Test
  public void handlingTheSameEventTwiceSendsTwoIdenticalCommands() {
    saga.on(orderPlaced());
    saga.on(orderPlaced());

    var authorizePayment = new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), totalPrice);
    var reserveStock = new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems());

    sent.shouldReceiveMessages(authorizePayment, reserveStock, authorizePayment, reserveStock);
  }

  // Commit phase

  @Test
  public void aConfirmedOrderSendsThePackageBeforeAnyMoneyMoves() {
    saga.on(new OrderEvent.OrderConfirmed(orderId, paymentId, shipmentId, now));

    sent.shouldReceiveMessages(new ShipmentCommand.RequestFulfillment(shipmentId));
  }

  @Test
  public void aSentPackageIsRecordedAgainstTheOrder() {
    saga.on(new ShipmentExternalEvent.PackageWasSent(
      shipmentId, orderId.value(), carrier, trackingNumber, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPackageSent(orderId, carrier, trackingNumber, now));
  }

  @Test
  public void theOrdersPaymentDueEventIsWhatCapturesThePayment() {
    saga.on(new OrderEvent.OrderPaymentDue(orderId, paymentId, now));

    sent.shouldReceiveMessages(new PaymentCommand.CapturePayment(paymentId));
  }

  @Test
  public void aCapturedPaymentIsRecordedAgainstTheOrder() {
    saga.on(new PaymentExternalEvent.PaymentCaptured(
      orderId.value(), paymentId, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentCapture(orderId, now));
  }


  // Compensation

  @Test
  public void aFailedPaymentIsRecordedRatherThanCancellingTheOrderDirectly() {
    saga.on(new PaymentExternalEvent.PaymentFailed(
      orderId.value(), paymentId, now
    ));

    sent.shouldReceiveMessages(new RecordOrderPaymentFailure(orderId, now));
  }

  @Test
  public void anOutOfStockProductIsRecordedRatherThanCancellingTheOrderDirectly() {
    saga.on(new ShipmentExternalEvent.ShipmentFailed(
      shipmentId, orderId.value(), ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock, now
    ));

    sent.shouldReceiveMessages(
      new RecordOrderShipmentFailure(orderId, OrderCancellationReason.ProductWasOutOfStock, now)
    );
  }

  @Test
  public void aRejectedFulfillmentIsRecordedAsCouldNotShip() {
    saga.on(new ShipmentExternalEvent.ShipmentFailed(
      shipmentId, orderId.value(), ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected, now
    ));

    sent.shouldReceiveMessages(
      new RecordOrderShipmentFailure(orderId, OrderCancellationReason.CouldNotShip, now)
    );
  }

  // Paying again

  @Test
  public void aRetriedPaymentAsksForTheNewPaymentToBeAuthorized() {
    var retriedPaymentId = PaymentId.derivedFrom(orderId.value(), 2);

    saga.on(new OrderEvent.OrderPaymentRetried(orderId, retriedPaymentId, totalPrice, now));

    sent.shouldReceiveMessages(
      new PaymentCommand.AuthorizePayment(retriedPaymentId, orderId.value(), totalPrice)
    );
  }


  @Test
  public void aCancelledOrderWithAnAuthorizedHoldVoidsItAndReleasesTheStock() {
    saga.on(orderCancelled(OrderPaymentStatus.Authorized, OrderShipmentStatus.Reserved));

    sent.shouldReceiveMessages(
      new PaymentCommand.VoidPayment(paymentId),
      new ShipmentCommand.ReleaseStock(shipmentId)
    );
  }

  @Test
  public void aCancelledOrderWithOnlyTheCardHeldVoidsThatHold() {
    saga.on(orderCancelled(OrderPaymentStatus.Authorized, OrderShipmentStatus.NotReserved));

    sent.shouldReceiveMessages(new PaymentCommand.VoidPayment(paymentId));
  }

  @Test
  public void aCancelledOrderThatTookNoHoldsUndoesNothing() {
    saga.on(orderCancelled(OrderPaymentStatus.NotAuthorized, OrderShipmentStatus.NotReserved));

    sent.shouldNotReceiveAnyEvent();
  }

  private OrderEvent.OrderPlaced orderPlaced() {
    return new OrderEvent.OrderPlaced(
      orderId, cartId, clientId, orderItems, totalPrice, now
    );
  }

  private OrderEvent.OrderCancelled orderCancelled(
    OrderPaymentStatus paymentStatus,
    OrderShipmentStatus shipmentStatus
  ) {
    return new OrderEvent.OrderCancelled(
      orderId,
      paymentId,
      paymentStatus,
      shipmentId,
      shipmentStatus,
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
