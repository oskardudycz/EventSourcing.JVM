package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

public class OrderTests extends AggregateSpecification<Order, OrderEvent, OrderId> {
  private final OrderId orderId = OrderId.of(UUID.randomUUID());
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final PricedProductItem[] productItems = new PricedProductItem[]{
    new PricedProductItem(UUID.randomUUID(), 2, 12.5)
  };
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime authorizedAt = now.plusMinutes(1);
  private final OffsetDateTime reservedAt = now.plusMinutes(2);
  private final OffsetDateTime sentAt = now.plusMinutes(3);
  private final OffsetDateTime capturedAt = now.plusMinutes(4);
  private final OffsetDateTime deliveredAt = now.plusMinutes(5);

  private final OrderInitialized initialized =
    new OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now);
  private final OrderPaymentAuthorized paymentAuthorized =
    new OrderPaymentAuthorized(orderId, paymentId, authorizedAt);
  private final OrderStockReserved stockReserved =
    new OrderStockReserved(orderId, shipmentId, reservedAt);
  private final OrderConfirmed confirmed = new OrderConfirmed(orderId, shipmentId, reservedAt);
  private final OrderPackageSent packageSent = new OrderPackageSent(orderId, paymentId, sentAt);
  private final OrderPaymentCaptured paymentCaptured =
    new OrderPaymentCaptured(orderId, paymentId, totalPrice, capturedAt);
  private final OrderShipmentDelivered delivered =
    new OrderShipmentDelivered(orderId, shipmentId, deliveredAt);

  protected OrderTests() {
    super(Order::empty);
  }

  @Test
  public void initializingEmitsOrderInitializedCarryingTheCartItCameFrom() {
    // Given
    given()
      // When
      .when(current -> current.initialize(
        orderId, cartId, clientId, productItems, totalPrice, now
      ))
      // Then
      .then(initialized);
  }

  @Test
  public void initializingAnInitializedOrderEmitsNothing() {
    // Given
    given(initialized)
      // When
      .when(current -> current.initialize(
        orderId, cartId, clientId, productItems, totalPrice, now
      ))
      // Then
      .thenNothing();
  }

  // The hold phase, and the join that ends it

  @Test
  public void theFirstHoldOnItsOwnDoesNotConfirmTheOrder() {
    // Given
    given(initialized)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .then(paymentAuthorized);
  }

  @Test
  public void theStockReservationArrivingLastConfirmsTheOrder() {
    // Given
    given(initialized, paymentAuthorized)
      // When
      .when(current -> current.recordStockReservation(shipmentId, reservedAt))
      // Then
      .then(stockReserved, confirmed);
  }

  @Test
  public void theAuthorizationArrivingLastConfirmsTheOrderJustTheSame() {
    // Given
    given(initialized, stockReserved)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .then(paymentAuthorized, new OrderConfirmed(orderId, shipmentId, authorizedAt));
  }

  @Test
  public void recordingTheSameAuthorizationTwiceEmitsNothing() {
    // Given
    given(initialized, paymentAuthorized)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void recordingTheSameReservationTwiceEmitsNothing() {
    // Given
    given(initialized, stockReserved)
      // When
      .when(current -> current.recordStockReservation(shipmentId, reservedAt))
      // Then
      .thenNothing();
  }

  // The commit phase, and the join that completes the order

  @Test
  public void recordingTheDispatchRepublishesThePaymentTheShipmentDoesNotKnow() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.recordPackageSent(sentAt))
      // Then
      .then(packageSent);
  }

  @Test
  public void recordingADispatchBeforeTheOrderIsConfirmedEmitsNothing() {
    // Given
    given(initialized, paymentAuthorized)
      // When
      .when(current -> current.recordPackageSent(sentAt))
      // Then
      .thenNothing();
  }

  @Test
  public void theDeliveryArrivingLastCompletesTheOrder() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
      // When
      .when(current -> current.recordDelivery(deliveredAt))
      // Then
      .then(delivered, new OrderCompleted(orderId, deliveredAt));
  }

  @Test
  public void theCaptureArrivingLastCompletesTheOrderJustTheSame() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent, delivered)
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .then(paymentCaptured, new OrderCompleted(orderId, capturedAt));
  }

  @Test
  public void aCaptureOnItsOwnDoesNotCompleteTheOrder() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .then(paymentCaptured);
  }

  @Test
  public void recordingADeliveryTwiceEmitsNothing() {
    // Given
    given(
      initialized, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured,
      delivered, new OrderCompleted(orderId, deliveredAt)
    )
      // When
      .when(current -> current.recordDelivery(deliveredAt))
      // Then
      .thenNothing();
  }

  // Compensation — the order waits for the other participant, then says what must be undone

  @Test
  public void aFailedPaymentWaitsForTheShipmentBeforeItCancels() {
    // Given
    given(initialized)
      // When
      .when(current -> current.recordPaymentFailure(authorizedAt))
      // Then
      .then(new OrderPaymentFailed(orderId, authorizedAt));
  }

  @Test
  public void aFailedPaymentWithStockAlreadyReservedCancelsAndAsksForThatStockBack() {
    // Given
    given(initialized, stockReserved)
      // When
      .when(current -> current.recordPaymentFailure(authorizedAt))
      // Then
      .then(
        new OrderPaymentFailed(orderId, authorizedAt),
        new OrderCancelled(
          orderId,
          null,
          OrderPaymentState.NotAuthorized,
          shipmentId,
          OrderShipmentState.Reserved,
          OrderCancellationReason.PaymentFailed,
          authorizedAt
        )
      );
  }

  @Test
  public void anOutOfStockShipmentWithTheCardAlreadyAuthorizedCancelsAndVoidsThatHold() {
    // Given
    given(initialized, paymentAuthorized)
      // When
      .when(current -> current.recordShipmentFailure(reservedAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, reservedAt),
        new OrderCancelled(
          orderId,
          paymentId,
          OrderPaymentState.Authorized,
          null,
          OrderShipmentState.NotReserved,
          OrderCancellationReason.ProductWasOutOfStock,
          reservedAt
        )
      );
  }

  @Test
  public void anExpiredReservationCancelsAConfirmedOrderAndVoidsTheHold() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.recordShipmentFailure(sentAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, sentAt),
        new OrderCancelled(
          orderId,
          paymentId,
          OrderPaymentState.Authorized,
          shipmentId,
          OrderShipmentState.NotReserved,
          OrderCancellationReason.ProductWasOutOfStock,
          sentAt
        )
      );
  }

  @Test
  public void bothParticipantsFailingCancelsTheOrderOnce() {
    // Given
    given(initialized, new OrderPaymentFailed(orderId, authorizedAt))
      // When
      .when(current -> current.recordShipmentFailure(reservedAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, reservedAt),
        new OrderCancelled(
          orderId,
          null,
          OrderPaymentState.NotAuthorized,
          null,
          OrderShipmentState.NotReserved,
          OrderCancellationReason.PaymentFailed,
          reservedAt
        )
      );
  }

  @Test
  public void aShipmentFailureAfterTheParcelLeftEmitsNothing() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.recordShipmentFailure(sentAt))
      // Then
      .thenNothing();
  }

  @Test
  public void aPaymentFailureAfterTheCaptureEmitsNothing() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
      // When
      .when(current -> current.recordPaymentFailure(capturedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void anOperatorCancellingAnUntouchedOrderLeavesNothingToUndo() {
    // Given
    given(initialized)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .then(new OrderCancelled(
        orderId,
        null,
        OrderPaymentState.NotAuthorized,
        null,
        OrderShipmentState.NotReserved,
        OrderCancellationReason.Requested,
        now
      ));
  }

  @Test
  public void anOperatorCancellingAConfirmedOrderAsksForBothHoldsBack() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .then(new OrderCancelled(
        orderId,
        paymentId,
        OrderPaymentState.Authorized,
        shipmentId,
        OrderShipmentState.Reserved,
        OrderCancellationReason.Requested,
        now
      ));
  }

  @Test
  public void anOperatorCancellingAfterTheCaptureAsksForARefund() {
    // Given
    given(initialized, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .then(new OrderCancelled(
        orderId,
        paymentId,
        OrderPaymentState.Captured,
        shipmentId,
        OrderShipmentState.Sent,
        OrderCancellationReason.Requested,
        now
      ));
  }

  @Test
  public void cancellingACompletedOrderEmitsNothing() {
    // Given
    given(
      initialized, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured,
      delivered, new OrderCompleted(orderId, deliveredAt)
    )
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .thenNothing();
  }

  @Test
  public void aHoldRecordedAfterTheOrderWasCancelledEmitsNothing() {
    // Given
    given(
      initialized,
      new OrderShipmentFailed(orderId, reservedAt),
      new OrderPaymentFailed(orderId, authorizedAt),
      new OrderCancelled(
        orderId,
        null,
        OrderPaymentState.NotAuthorized,
        null,
        OrderShipmentState.NotReserved,
        OrderCancellationReason.PaymentFailed,
        authorizedAt
      )
    )
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .thenNothing();
  }
}
