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
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime authorizedAt = now.plusMinutes(1);
  private final OffsetDateTime reservedAt = now.plusMinutes(2);
  private final OffsetDateTime sentAt = now.plusMinutes(3);
  private final OffsetDateTime capturedAt = now.plusMinutes(4);
  private final OffsetDateTime retriedAt = now.plusMinutes(5);
  private final OffsetDateTime closedAt = now.plusDays(14);
  private final PaymentId retriedPaymentId = PaymentId.derivedFrom(orderId.value(), 2);

  private final OrderPlaced placed =
    new OrderPlaced(orderId, cartId, clientId, productItems, totalPrice, now);
  private final OrderPaymentAuthorized paymentAuthorized =
    new OrderPaymentAuthorized(orderId, paymentId, authorizedAt);
  private final OrderStockReserved stockReserved =
    new OrderStockReserved(orderId, shipmentId, reservedAt);
  private final OrderConfirmed confirmed =
    new OrderConfirmed(orderId, paymentId, shipmentId, reservedAt);
  private final OrderPaymentCaptured paymentCaptured =
    new OrderPaymentCaptured(orderId, paymentId, totalPrice, capturedAt);
  private final OrderPackageSent packageSent =
    new OrderPackageSent(orderId, carrier, trackingNumber, sentAt);
  private final OrderPaymentDue paymentDue =
    new OrderPaymentDue(orderId, paymentId, sentAt);
  private final OrderPaymentFailed latePaymentFailure =
    new OrderPaymentFailed(orderId, capturedAt);
  private final OrderPaymentRetried paymentRetried =
    new OrderPaymentRetried(orderId, retriedPaymentId, totalPrice, retriedAt);

  protected OrderTests() {
    super(Order::empty);
  }

  @Test
  public void placingEmitsOrderPlacedCarryingTheCartItCameFrom() {
    // Given
    given()
      // When
      .when(current -> current.place(
        orderId, cartId, clientId, productItems, totalPrice, now
      ))
      // Then
      .then(placed);
  }

  @Test
  public void placingAPlacedOrderEmitsNothing() {
    // Given
    given(placed)
      // When
      .when(current -> current.place(
        orderId, cartId, clientId, productItems, totalPrice, now
      ))
      // Then
      .thenNothing();
  }

  // The hold phase, and the join that ends it

  @Test
  public void theFirstHoldOnItsOwnDoesNotConfirmTheOrder() {
    // Given
    given(placed)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .then(paymentAuthorized);
  }

  @Test
  public void theStockReservationArrivingLastConfirmsTheOrder() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.recordStockReservation(shipmentId, reservedAt))
      // Then
      .then(stockReserved, confirmed);
  }

  @Test
  public void theAuthorizationArrivingLastConfirmsTheOrderJustTheSame() {
    // Given
    given(placed, stockReserved)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .then(paymentAuthorized, new OrderConfirmed(orderId, paymentId, shipmentId, authorizedAt));
  }

  @Test
  public void recordingTheSameAuthorizationTwiceEmitsNothing() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void recordingTheSameReservationTwiceEmitsNothing() {
    // Given
    given(placed, stockReserved)
      // When
      .when(current -> current.recordStockReservation(shipmentId, reservedAt))
      // Then
      .thenNothing();
  }

  // The commit phase

  @Test
  public void recordingTheDispatchOfAConfirmedOrderMakesThePaymentDue() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.recordPackageSent(carrier, trackingNumber, sentAt))
      // Then
      .then(packageSent, paymentDue);
  }

  @Test
  public void recordingADispatchBeforeTheOrderIsConfirmedEmitsNothing() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.recordPackageSent(carrier, trackingNumber, sentAt))
      // Then
      .thenNothing();
  }

  @Test
  public void recordingTheSameDispatchTwiceEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.recordPackageSent(carrier, trackingNumber, sentAt))
      // Then
      .thenNothing();
  }

  @Test
  public void theCaptureAfterTheDispatchCompletesTheOrder() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .then(paymentCaptured, new OrderCompleted(orderId, capturedAt));
  }

  @Test
  public void recordingACaptureBeforeTheDispatchEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void recordingACaptureBeforeTheOrderIsConfirmedEmitsNothing() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .thenNothing();
  }





  // Compensation — the order waits for the other participant, then says what must be undone

  @Test
  public void aFailedPaymentWaitsForTheShipmentBeforeItCancels() {
    // Given
    given(placed)
      // When
      .when(current -> current.recordPaymentFailure(authorizedAt))
      // Then
      .then(new OrderPaymentFailed(orderId, authorizedAt));
  }

  @Test
  public void aFailedPaymentWithStockAlreadyReservedCancelsAndAsksForThatStockBack() {
    // Given
    given(placed, stockReserved)
      // When
      .when(current -> current.recordPaymentFailure(authorizedAt))
      // Then
      .then(
        new OrderPaymentFailed(orderId, authorizedAt),
        new OrderCancelled(
          orderId,
          null,
          OrderPaymentStatus.NotAuthorized,
          shipmentId,
          OrderShipmentStatus.Reserved,
          OrderCancellationReason.PaymentFailed,
          authorizedAt
        )
      );
  }

  @Test
  public void anOutOfStockShipmentWithTheCardAlreadyAuthorizedCancelsAndVoidsThatHold() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.recordShipmentFailure(OrderCancellationReason.ProductWasOutOfStock, reservedAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
        new OrderCancelled(
          orderId,
          paymentId,
          OrderPaymentStatus.Authorized,
          null,
          OrderShipmentStatus.NotReserved,
          OrderCancellationReason.ProductWasOutOfStock,
          reservedAt
        )
      );
  }


  @Test
  public void bothParticipantsFailingCancelsTheOrderOnce() {
    // Given
    given(placed, new OrderPaymentFailed(orderId, authorizedAt))
      // When
      .when(current -> current.recordShipmentFailure(OrderCancellationReason.ProductWasOutOfStock, reservedAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
        new OrderCancelled(
          orderId,
          null,
          OrderPaymentStatus.NotAuthorized,
          null,
          OrderShipmentStatus.NotReserved,
          OrderCancellationReason.PaymentFailed,
          reservedAt
        )
      );
  }

  @Test
  public void aShipmentFailureAfterTheParcelLeftEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.recordShipmentFailure(OrderCancellationReason.ProductWasOutOfStock, sentAt))
      // Then
      .thenNothing();
  }

  @Test
  public void aPaymentFailureAfterTheParcelLeftIsRecordedButDoesNotCancelTheOrder() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.recordPaymentFailure(capturedAt))
      // Then
      .then(new OrderPaymentFailed(orderId, capturedAt));
  }


  @Test
  public void aCaptureAfterALatePaymentFailureEmitsNothing() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent,
      latePaymentFailure
    )
      // When
      .when(current -> current.recordPaymentCapture(capturedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void aPaymentFailureAfterTheCaptureEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
      // When
      .when(current -> current.recordPaymentFailure(capturedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void cancellingAnUntouchedOrderLeavesNothingToUndo() {
    // Given
    given(placed)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .then(new OrderCancelled(
        orderId,
        null,
        OrderPaymentStatus.NotAuthorized,
        null,
        OrderShipmentStatus.NotReserved,
        OrderCancellationReason.Requested,
        now
      ));
  }

  @Test
  public void cancellingBeforeConfirmationAsksForTheHoldAlreadyTakenBack() {
    // Given
    given(placed, paymentAuthorized)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .then(new OrderCancelled(
        orderId,
        paymentId,
        OrderPaymentStatus.Authorized,
        null,
        OrderShipmentStatus.NotReserved,
        OrderCancellationReason.Requested,
        now
      ));
  }

  @Test
  public void cancellingAConfirmedOrderEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .thenNothing();
  }

  @Test
  public void cancellingAfterTheParcelLeftEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
      // When
      .when(current -> current.cancel(OrderCancellationReason.Requested, now))
      // Then
      .thenNothing();
  }

  @Test
  public void cancellingACompletedOrderEmitsNothing() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, paymentCaptured,
      new OrderCompleted(orderId, capturedAt)
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
      placed,
      new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
      new OrderPaymentFailed(orderId, authorizedAt),
      new OrderCancelled(
        orderId,
        null,
        OrderPaymentStatus.NotAuthorized,
        null,
        OrderShipmentStatus.NotReserved,
        OrderCancellationReason.PaymentFailed,
        authorizedAt
      )
    )
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, authorizedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void aRejectedFulfillmentCancelsAConfirmedOrderAndAsksForBothHoldsBack() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed)
      // When
      .when(current -> current.recordShipmentFailure(OrderCancellationReason.CouldNotShip, sentAt))
      // Then
      .then(
        new OrderShipmentFailed(orderId, OrderCancellationReason.CouldNotShip, sentAt),
        new OrderCancelled(
          orderId,
          paymentId,
          OrderPaymentStatus.Authorized,
          shipmentId,
          OrderShipmentStatus.Reserved,
          OrderCancellationReason.CouldNotShip,
          sentAt
        )
      );
  }

  // Paying again — the parcel left, the money did not come

  @Test
  public void retryingAPaymentThatFailedAfterTheParcelLeftAsksForANewPayment() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
      // When
      .when(current -> current.retryPayment(retriedAt))
      // Then
      .then(paymentRetried);
  }

  @Test
  public void retryingAPaymentThatHasNotFailedEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
      // When
      .when(current -> current.retryPayment(retriedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void retryingBeforeTheParcelLeftEmitsNothing() {
    // Given
    given(placed, new OrderPaymentFailed(orderId, authorizedAt))
      // When
      .when(current -> current.retryPayment(retriedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void retryingWhileARetryIsRunningEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried)
      // When
      .when(current -> current.retryPayment(retriedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void theNewAuthorizationMakesThePaymentDue() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried)
      // When
      .when(current -> current.recordPaymentAuthorization(retriedPaymentId, retriedAt))
      // Then
      .then(
        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
        new OrderPaymentDue(orderId, retriedPaymentId, retriedAt)
      );
  }

  @Test
  public void anAuthorizationOfTheFailedPaymentDuringARetryEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried)
      // When
      .when(current -> current.recordPaymentAuthorization(paymentId, retriedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void capturingTheNewPaymentCompletesTheOrder() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried,
      new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
      new OrderPaymentDue(orderId, retriedPaymentId, retriedAt)
    )
      // When
      .when(current -> current.recordPaymentCapture(retriedAt))
      // Then
      .then(
        new OrderPaymentCaptured(orderId, retriedPaymentId, totalPrice, retriedAt),
        new OrderCompleted(orderId, retriedAt)
      );
  }

  @Test
  public void aFailedRetryWaitsForTheClientAgain() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried)
      // When
      .when(current -> current.recordPaymentFailure(retriedAt))
      // Then
      .then(new OrderPaymentFailed(orderId, retriedAt));
  }

  @Test
  public void aSecondRetryAsksForTheThirdPayment() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried,
      new OrderPaymentFailed(orderId, retriedAt)
    )
      // When
      .when(current -> current.retryPayment(retriedAt))
      // Then
      .then(new OrderPaymentRetried(orderId, PaymentId.derivedFrom(orderId.value(), 3), totalPrice, retriedAt));
  }

  @Test
  public void closingAnOrderStillUnpaidAfterTheParcelLeftEmitsOrderClosedUnpaid() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
      // When
      .when(current -> current.closeUnpaid(closedAt))
      // Then
      .then(new OrderClosedUnpaid(orderId, closedAt));
  }

  @Test
  public void closingWhileARetryIsRunningEmitsOrderClosedUnpaid() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried)
      // When
      .when(current -> current.closeUnpaid(closedAt))
      // Then
      .then(new OrderClosedUnpaid(orderId, closedAt));
  }

  @Test
  public void closingAnOrderWhosePaymentHasNotFailedEmitsNothing() {
    // Given
    given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
      // When
      .when(current -> current.closeUnpaid(closedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void closingACompletedOrderEmitsNothing() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, paymentCaptured,
      new OrderCompleted(orderId, capturedAt)
    )
      // When
      .when(current -> current.closeUnpaid(closedAt))
      // Then
      .thenNothing();
  }

  @Test
  public void anAuthorizationAfterTheOrderWasClosedUnpaidEmitsNothing() {
    // Given
    given(
      placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure, paymentRetried,
      new OrderClosedUnpaid(orderId, closedAt)
    )
      // When
      .when(current -> current.recordPaymentAuthorization(retriedPaymentId, closedAt))
      // Then
      .thenNothing();
  }
}
