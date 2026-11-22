package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.ecommerce.orders.OrderDecider.decide;
import static io.eventdriven.ecommerce.orders.OrderEvent.*;

import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.eventsourcing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class OrderTests extends EventSourcedSpecification<Order, OrderEvent> {
    private final Order.Id orderId = Order.Id.of(UUID.randomUUID());
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderId.value());
    private final Order.PricedProductItem[] productItems =
            new Order.PricedProductItem[] {new Order.PricedProductItem(UUID.randomUUID(), 2, 12.5)};
    private final double totalPrice = 25;
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime authorizedAt = now.plusMinutes(1);
    private final OffsetDateTime reservedAt = now.plusMinutes(2);
    private final OffsetDateTime sentAt = now.plusMinutes(3);
    private final OffsetDateTime capturedAt = now.plusMinutes(4);
    private final OffsetDateTime retriedAt = now.plusMinutes(5);
    private final OffsetDateTime dueAt = now.plusDays(14);
    private final OffsetDateTime closedAt = dueAt;
    private final Payment.Id retriedPaymentId = Payment.Id.derivedFrom(orderId.value(), 2);

    private final OrderPlaced placed = new OrderPlaced(orderId, cartId, customerId, productItems, totalPrice, now);
    private final OrderPaymentAuthorized paymentAuthorized =
            new OrderPaymentAuthorized(orderId, paymentId, authorizedAt);
    private final OrderStockReserved stockReserved = new OrderStockReserved(orderId, shipmentId, reservedAt);
    private final OrderConfirmed confirmed = new OrderConfirmed(orderId, paymentId, shipmentId, reservedAt);
    private final OrderPaymentCaptured paymentCaptured =
            new OrderPaymentCaptured(orderId, paymentId, totalPrice, capturedAt);
    private final OrderPackageSent packageSent = new OrderPackageSent(orderId, carrier, trackingNumber, sentAt);
    private final OrderPaymentDue paymentDue = new OrderPaymentDue(orderId, paymentId, sentAt);
    private final OrderPaymentFailed latePaymentFailure = new OrderPaymentFailed(orderId, capturedAt, dueAt);
    private final OrderPaymentFailed unpaid = new OrderPaymentFailed(orderId, authorizedAt, dueAt);
    private final OrderPaymentRetried paymentRetried =
            new OrderPaymentRetried(orderId, retriedPaymentId, totalPrice, retriedAt);

    protected OrderTests() {
        super(Order::initial, Order::evolve);
    }

    @Test
    public void placingEmitsOrderPlacedCarryingTheCartItCameFrom() {
        // Given
        given()
                // When
                .when(current ->
                        decide(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice, now), current))
                // Then
                .then(placed);
    }

    @Test
    public void placingAPlacedOrderEmitsNothing() {
        // Given
        given(placed)
                // When
                .when(current ->
                        decide(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice, now), current))
                // Then
                .thenNothing();
    }

    // The hold phase, and the join that ends it

    @Test
    public void theFirstHoldOnItsOwnDoesNotConfirmTheOrder() {
        // Given
        given(placed)
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt), current))
                // Then
                .then(paymentAuthorized);
    }

    @Test
    public void theStockReservationArrivingLastConfirmsTheOrder() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(new RecordOrderStockReservation(orderId, shipmentId, reservedAt), current))
                // Then
                .then(stockReserved, confirmed);
    }

    @Test
    public void theAuthorizationArrivingLastConfirmsTheOrderJustTheSame() {
        // Given
        given(placed, stockReserved)
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt), current))
                // Then
                .then(paymentAuthorized, new OrderConfirmed(orderId, paymentId, shipmentId, authorizedAt));
    }

    @Test
    public void recordingTheSameAuthorizationTwiceEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingTheSameReservationTwiceEmitsNothing() {
        // Given
        given(placed, stockReserved)
                // When
                .when(current -> decide(new RecordOrderStockReservation(orderId, shipmentId, reservedAt), current))
                // Then
                .thenNothing();
    }

    // The commit phase

    @Test
    public void recordingTheDispatchOfAConfirmedOrderMakesThePaymentDue() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(current -> decide(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt), current))
                // Then
                .then(packageSent, paymentDue);
    }

    @Test
    public void recordingADispatchBeforeTheOrderIsConfirmedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingTheSameDispatchTwiceEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(current -> decide(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void theCaptureAfterTheDispatchCompletesTheOrder() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt), current))
                // Then
                .then(paymentCaptured, new OrderCompleted(orderId, capturedAt));
    }

    @Test
    public void recordingACaptureBeforeTheDispatchEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingACaptureBeforeTheOrderIsConfirmedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt), current))
                // Then
                .thenNothing();
    }

    // Compensation — the order waits for the other participant, then says what must be undone

    @Test
    public void aFailedPaymentBeforeConfirmationKeepsTheOrderOpenForTheCustomerToPayAgain() {
        // Given
        given(placed)
                // When
                .when(current ->
                        decide(new RecordOrderPaymentFailure(orderId, paymentId, authorizedAt, dueAt), current))
                // Then
                .then(new OrderPaymentFailed(orderId, authorizedAt, dueAt));
    }

    @Test
    public void aFailedPaymentWithStockAlreadyReservedKeepsTheOrderOpenAndTheStockHeld() {
        // Given
        given(placed, stockReserved)
                // When
                .when(current ->
                        decide(new RecordOrderPaymentFailure(orderId, paymentId, authorizedAt, dueAt), current))
                // Then
                .then(new OrderPaymentFailed(orderId, authorizedAt, dueAt));
    }

    @Test
    public void aLapsedHoldAfterConfirmationBeforeTheParcelLeftCancelsAndAsksForTheStockBack() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(current -> decide(new RecordOrderPaymentFailure(orderId, paymentId, sentAt, dueAt), current))
                // Then
                .then(
                        new OrderPaymentFailed(orderId, sentAt, dueAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.NotAuthorized,
                                shipmentId,
                                Order.ShipmentStatus.Reserved,
                                Order.CancellationReason.PaymentFailed,
                                sentAt));
    }

    @Test
    public void anOutOfStockShipmentWithTheCardAlreadyAuthorizedCancelsAndCancelsThatHold() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(
                        new RecordOrderShipmentFailure(
                                orderId, Order.CancellationReason.ProductWasOutOfStock, reservedAt),
                        current))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.Authorized,
                                null,
                                Order.ShipmentStatus.NotReserved,
                                Order.CancellationReason.ProductWasOutOfStock,
                                reservedAt));
    }

    @Test
    public void bothParticipantsFailingCancelsTheOrderOnce() {
        // Given
        given(placed, new OrderPaymentFailed(orderId, authorizedAt, dueAt))
                // When
                .when(current -> decide(
                        new RecordOrderShipmentFailure(
                                orderId, Order.CancellationReason.ProductWasOutOfStock, reservedAt),
                        current))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.NotAuthorized,
                                null,
                                Order.ShipmentStatus.NotReserved,
                                Order.CancellationReason.PaymentFailed,
                                reservedAt));
    }

    @Test
    public void aShipmentFailureAfterTheParcelLeftEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(current -> decide(
                        new RecordOrderShipmentFailure(orderId, Order.CancellationReason.ProductWasOutOfStock, sentAt),
                        current))
                // Then
                .thenNothing();
    }

    @Test
    public void aPaymentFailureAfterTheParcelLeftIsRecordedButDoesNotCancelTheOrder() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(current -> decide(new RecordOrderPaymentFailure(orderId, paymentId, capturedAt, dueAt), current))
                // Then
                .then(new OrderPaymentFailed(orderId, capturedAt, dueAt));
    }

    @Test
    public void aCaptureAfterALatePaymentFailureEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, latePaymentFailure)
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aPaymentFailureAfterTheCaptureEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
                // When
                .when(current -> decide(new RecordOrderPaymentFailure(orderId, paymentId, capturedAt, dueAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void cancellingAnUntouchedOrderLeavesNothingToUndo() {
        // Given
        given(placed)
                // When
                .when(current -> decide(new CancelOrder(orderId, Order.CancellationReason.Requested, now), current))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        Order.PaymentStatus.NotAuthorized,
                        null,
                        Order.ShipmentStatus.NotReserved,
                        Order.CancellationReason.Requested,
                        now));
    }

    @Test
    public void cancellingBeforeConfirmationAsksForTheHoldAlreadyTakenBack() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(current -> decide(new CancelOrder(orderId, Order.CancellationReason.Requested, now), current))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        Order.PaymentStatus.Authorized,
                        null,
                        Order.ShipmentStatus.NotReserved,
                        Order.CancellationReason.Requested,
                        now));
    }

    @Test
    public void cancellingAConfirmedOrderEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(current -> decide(new CancelOrder(orderId, Order.CancellationReason.Requested, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void cancellingAfterTheParcelLeftEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(current -> decide(new CancelOrder(orderId, Order.CancellationReason.Requested, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void cancellingACompletedOrderEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        paymentCaptured,
                        new OrderCompleted(orderId, capturedAt))
                // When
                .when(current -> decide(new CancelOrder(orderId, Order.CancellationReason.Requested, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aHoldRecordedAfterTheOrderWasCanceledEmitsNothing() {
        // Given
        given(
                        placed,
                        new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderPaymentFailed(orderId, authorizedAt, dueAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.NotAuthorized,
                                null,
                                Order.ShipmentStatus.NotReserved,
                                Order.CancellationReason.PaymentFailed,
                                authorizedAt))
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aRejectedFulfillmentCancelsAConfirmedOrderAndAsksForBothHoldsBack() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(current -> decide(
                        new RecordOrderShipmentFailure(orderId, Order.CancellationReason.CouldNotShip, sentAt),
                        current))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, Order.CancellationReason.CouldNotShip, sentAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.Authorized,
                                shipmentId,
                                Order.ShipmentStatus.Reserved,
                                Order.CancellationReason.CouldNotShip,
                                sentAt));
    }

    // Paying again — the parcel left, the money did not come

    @Test
    public void retryingAPaymentThatFailedAfterTheParcelLeftAsksForANewPayment() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .then(paymentRetried);
    }

    @Test
    public void retryingAPaymentThatHasNotFailedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void retryingAnUnpaidOrderBeforeConfirmationAsksForANewPayment() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .then(paymentRetried);
    }

    @Test
    public void retryingACanceledOrderEmitsNothing() {
        // Given
        given(
                        placed,
                        unpaid,
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                Order.PaymentStatus.NotAuthorized,
                                null,
                                Order.ShipmentStatus.NotReserved,
                                Order.CancellationReason.Requested,
                                retriedAt))
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void theRetriedPaymentAuthorizedBeforeConfirmationConfirmsTheOrder() {
        // Given
        given(placed, stockReserved, unpaid, paymentRetried)
                // When
                .when(current ->
                        decide(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt), current))
                // Then
                .then(
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderConfirmed(orderId, retriedPaymentId, shipmentId, retriedAt));
    }

    @Test
    public void anAuthorizationOfTheFailedPaymentBeforeConfirmationEmitsNothing() {
        // Given
        given(placed, stockReserved, unpaid, paymentRetried)
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aStockFailureWhileTheRetriedPaymentIsPendingWaitsForItsAnswer() {
        // Given
        given(placed, unpaid, paymentRetried)
                // When
                .when(current -> decide(
                        new RecordOrderShipmentFailure(
                                orderId, Order.CancellationReason.ProductWasOutOfStock, retriedAt),
                        current))
                // Then
                .then(new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, retriedAt));
    }

    @Test
    public void theRetriedPaymentAuthorizedAfterAStockFailureCancelsAndAsksForThatHoldBack() {
        // Given
        given(
                        placed,
                        unpaid,
                        paymentRetried,
                        new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, retriedAt))
                // When
                .when(current ->
                        decide(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt), current))
                // Then
                .then(
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderCanceled(
                                orderId,
                                retriedPaymentId,
                                Order.PaymentStatus.Authorized,
                                null,
                                Order.ShipmentStatus.NotReserved,
                                Order.CancellationReason.ProductWasOutOfStock,
                                retriedAt));
    }

    @Test
    public void retryingWhileARetryIsRunningEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void theNewAuthorizationMakesThePaymentDue() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current ->
                        decide(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt), current))
                // Then
                .then(
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderPaymentDue(orderId, retriedPaymentId, retriedAt));
    }

    @Test
    public void anAuthorizationOfTheFailedPaymentDuringARetryEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(new RecordOrderPaymentAuthorization(orderId, paymentId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void capturingTheNewPaymentCompletesTheOrder() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried,
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderPaymentDue(orderId, retriedPaymentId, retriedAt))
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, retriedPaymentId, retriedAt), current))
                // Then
                .then(
                        new OrderPaymentCaptured(orderId, retriedPaymentId, totalPrice, retriedAt),
                        new OrderCompleted(orderId, retriedAt));
    }

    @Test
    public void aFailedRetryWaitsForTheCustomerAgain() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current ->
                        decide(new RecordOrderPaymentFailure(orderId, retriedPaymentId, retriedAt, dueAt), current))
                // Then
                .then(new OrderPaymentFailed(orderId, retriedAt, dueAt));
    }

    @Test
    public void aFailedRetryKeepsTheDateTheFirstFailureGaveTheCustomer() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(
                        new RecordOrderPaymentFailure(orderId, retriedPaymentId, retriedAt, dueAt.plusDays(14)),
                        current))
                // Then
                .then(new OrderPaymentFailed(orderId, retriedAt, dueAt));
    }

    @Test
    public void aRepeatedFailureOfTheFirstPaymentDuringTheRetryRecordsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(new RecordOrderPaymentFailure(orderId, paymentId, retriedAt, dueAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aFailureOfTheFirstPaymentAfterTheSecondConfirmedTheOrderRecordsNothing() {
        // Given
        given(
                        placed,
                        stockReserved,
                        unpaid,
                        paymentRetried,
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderConfirmed(orderId, retriedPaymentId, shipmentId, retriedAt))
                // When
                .when(current -> decide(new RecordOrderPaymentFailure(orderId, paymentId, retriedAt, dueAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aCaptureOfTheFirstPaymentWhileTheSecondIsCurrentRecordsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried,
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderPaymentDue(orderId, retriedPaymentId, retriedAt))
                // When
                .when(current -> decide(new RecordOrderPaymentCapture(orderId, paymentId, retriedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void aSecondRetryAsksForTheThirdPayment() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried,
                        new OrderPaymentFailed(orderId, retriedAt, dueAt))
                // When
                .when(current -> decide(new RetryOrderPayment(orderId, retriedAt), current))
                // Then
                .then(new OrderPaymentRetried(
                        orderId, Payment.Id.derivedFrom(orderId.value(), 3), totalPrice, retriedAt));
    }

    @Test
    public void closingAnOrderStillUnpaidAfterTheParcelLeftEmitsOrderClosedUnpaid() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, closedAt), current))
                // Then
                .then(new OrderClosedUnpaid(orderId, closedAt));
    }

    @Test
    public void closingBeforeThePaymentIsDueEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, dueAt.minusMinutes(1)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void closingAnUnpaidOrderBeforeConfirmationCancelsItAndAsksForTheStockBack() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, dueAt), current))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        Order.PaymentStatus.NotAuthorized,
                        shipmentId,
                        Order.ShipmentStatus.Reserved,
                        Order.CancellationReason.PaymentFailed,
                        dueAt));
    }

    @Test
    public void closingAnUnpaidOrderBeforeConfirmationBeforeThePaymentIsDueEmitsNothing() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, dueAt.minusMinutes(1)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void closingAnUnpaidOrderWhileItsRetriedPaymentIsPendingCancelsIt() {
        // Given
        given(placed, stockReserved, unpaid, paymentRetried)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, dueAt), current))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        retriedPaymentId,
                        Order.PaymentStatus.NotAuthorized,
                        shipmentId,
                        Order.ShipmentStatus.Reserved,
                        Order.CancellationReason.PaymentFailed,
                        dueAt));
    }

    @Test
    public void cancelingAnUnpaidOrderAsksForTheStockBack() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(current ->
                        decide(new CancelOrder(orderId, Order.CancellationReason.Requested, retriedAt), current))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        Order.PaymentStatus.NotAuthorized,
                        shipmentId,
                        Order.ShipmentStatus.Reserved,
                        Order.CancellationReason.Requested,
                        retriedAt));
    }

    @Test
    public void closingWhileARetryIsRunningBeforeThePaymentIsDueEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, dueAt.minusMinutes(1)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void closingWhileARetryIsRunningEmitsOrderClosedUnpaid() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, closedAt), current))
                // Then
                .then(new OrderClosedUnpaid(orderId, closedAt));
    }

    @Test
    public void closingAnOrderWhosePaymentHasNotFailedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, closedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void closingACompletedOrderEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        paymentCaptured,
                        new OrderCompleted(orderId, capturedAt))
                // When
                .when(current -> decide(new CloseUnpaidOrder(orderId, closedAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void anAuthorizationAfterTheOrderWasClosedUnpaidEmitsNothing() {
        // Given
        given(
                        placed,
                        paymentAuthorized,
                        stockReserved,
                        confirmed,
                        packageSent,
                        paymentDue,
                        latePaymentFailure,
                        paymentRetried,
                        new OrderClosedUnpaid(orderId, closedAt))
                // When
                .when(current ->
                        decide(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, closedAt), current))
                // Then
                .thenNothing();
    }
}
