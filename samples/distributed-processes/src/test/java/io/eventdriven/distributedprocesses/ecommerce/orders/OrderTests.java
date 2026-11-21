package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class OrderTests extends EventSourcedSpecification<Order, OrderEvent> {
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();
    private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
    private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
    private final PricedProductItem[] productItems =
            new PricedProductItem[] {new PricedProductItem(UUID.randomUUID(), 2, 12.5)};
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
    private final PaymentId retriedPaymentId = PaymentId.derivedFrom(orderId.value(), 2);

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

    private Function<Order, OrderEvent[]> handling(OrderCommand command) {
        return handling(command, now);
    }

    private Function<Order, OrderEvent[]> handling(OrderCommand command, OffsetDateTime at) {
        return current -> new OrderDecider(failedAt -> dueAt, () -> at).handle(command, current);
    }

    protected OrderTests() {
        super(Order::initial, Order::evolve);
    }

    @Test
    public void placingEmitsOrderPlacedCarryingTheCartItCameFrom() {
        // Given
        given()
                // When
                .when(handling(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice)))
                // Then
                .then(placed);
    }

    @Test
    public void placingAPlacedOrderEmitsNothing() {
        // Given
        given(placed)
                // When
                .when(handling(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice)))
                // Then
                .thenNothing();
    }

    // The hold phase, and the join that ends it

    @Test
    public void theFirstHoldOnItsOwnDoesNotConfirmTheOrder() {
        // Given
        given(placed)
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)))
                // Then
                .then(paymentAuthorized);
    }

    @Test
    public void theStockReservationArrivingLastConfirmsTheOrder() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new RecordOrderStockReservation(orderId, shipmentId, reservedAt)))
                // Then
                .then(stockReserved, confirmed);
    }

    @Test
    public void theAuthorizationArrivingLastConfirmsTheOrderJustTheSame() {
        // Given
        given(placed, stockReserved)
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)))
                // Then
                .then(paymentAuthorized, new OrderConfirmed(orderId, paymentId, shipmentId, authorizedAt));
    }

    @Test
    public void recordingTheSameAuthorizationTwiceEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingTheSameReservationTwiceEmitsNothing() {
        // Given
        given(placed, stockReserved)
                // When
                .when(handling(new RecordOrderStockReservation(orderId, shipmentId, reservedAt)))
                // Then
                .thenNothing();
    }

    // The commit phase

    @Test
    public void recordingTheDispatchOfAConfirmedOrderMakesThePaymentDue() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(handling(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)))
                // Then
                .then(packageSent, paymentDue);
    }

    @Test
    public void recordingADispatchBeforeTheOrderIsConfirmedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingTheSameDispatchTwiceEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(handling(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void theCaptureAfterTheDispatchCompletesTheOrder() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(handling(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt)))
                // Then
                .then(paymentCaptured, new OrderCompleted(orderId, capturedAt));
    }

    @Test
    public void recordingACaptureBeforeTheDispatchEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(handling(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingACaptureBeforeTheOrderIsConfirmedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt)))
                // Then
                .thenNothing();
    }

    // Compensation — the order waits for the other participant, then says what must be undone

    @Test
    public void aFailedPaymentBeforeConfirmationKeepsTheOrderOpenForTheCustomerToPayAgain() {
        // Given
        given(placed)
                // When
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, authorizedAt)))
                // Then
                .then(new OrderPaymentFailed(orderId, authorizedAt, dueAt));
    }

    @Test
    public void aFailedPaymentWithStockAlreadyReservedKeepsTheOrderOpenAndTheStockHeld() {
        // Given
        given(placed, stockReserved)
                // When
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, authorizedAt)))
                // Then
                .then(new OrderPaymentFailed(orderId, authorizedAt, dueAt));
    }

    @Test
    public void aLapsedHoldAfterConfirmationBeforeTheParcelLeftCancelsAndAsksForTheStockBack() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, sentAt)))
                // Then
                .then(
                        new OrderPaymentFailed(orderId, sentAt, dueAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                OrderPaymentStatus.NotAuthorized,
                                shipmentId,
                                OrderShipmentStatus.Reserved,
                                OrderCancellationReason.PaymentFailed,
                                sentAt));
    }

    @Test
    public void anOutOfStockShipmentWithTheCardAlreadyAuthorizedCancelsAndCancelsThatHold() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new RecordOrderShipmentFailure(
                        orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt)))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                OrderPaymentStatus.Authorized,
                                null,
                                OrderShipmentStatus.NotReserved,
                                OrderCancellationReason.ProductWasOutOfStock,
                                reservedAt));
    }

    @Test
    public void bothParticipantsFailingCancelsTheOrderOnce() {
        // Given
        given(placed, new OrderPaymentFailed(orderId, authorizedAt, dueAt))
                // When
                .when(handling(new RecordOrderShipmentFailure(
                        orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt)))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                OrderPaymentStatus.NotAuthorized,
                                null,
                                OrderShipmentStatus.NotReserved,
                                OrderCancellationReason.PaymentFailed,
                                reservedAt));
    }

    @Test
    public void aShipmentFailureAfterTheParcelLeftEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(handling(
                        new RecordOrderShipmentFailure(orderId, OrderCancellationReason.ProductWasOutOfStock, sentAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void aPaymentFailureAfterTheParcelLeftIsRecordedButDoesNotCancelTheOrder() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, capturedAt)))
                // Then
                .then(new OrderPaymentFailed(orderId, capturedAt, dueAt));
    }

    @Test
    public void aCaptureAfterALatePaymentFailureEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, latePaymentFailure)
                // When
                .when(handling(new RecordOrderPaymentCapture(orderId, paymentId, capturedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void aPaymentFailureAfterTheCaptureEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentCaptured)
                // When
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, capturedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void cancellingAnUntouchedOrderLeavesNothingToUndo() {
        // Given
        given(placed)
                // When
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), now))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        OrderPaymentStatus.NotAuthorized,
                        null,
                        OrderShipmentStatus.NotReserved,
                        OrderCancellationReason.Requested,
                        now));
    }

    @Test
    public void cancellingBeforeConfirmationAsksForTheHoldAlreadyTakenBack() {
        // Given
        given(placed, paymentAuthorized)
                // When
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), now))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        OrderPaymentStatus.Authorized,
                        null,
                        OrderShipmentStatus.NotReserved,
                        OrderCancellationReason.Requested,
                        now));
    }

    @Test
    public void cancellingAConfirmedOrderEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), now))
                // Then
                .thenNothing();
    }

    @Test
    public void cancellingAfterTheParcelLeftEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent)
                // When
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), now))
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
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), now))
                // Then
                .thenNothing();
    }

    @Test
    public void aHoldRecordedAfterTheOrderWasCanceledEmitsNothing() {
        // Given
        given(
                        placed,
                        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt),
                        new OrderPaymentFailed(orderId, authorizedAt, dueAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                OrderPaymentStatus.NotAuthorized,
                                null,
                                OrderShipmentStatus.NotReserved,
                                OrderCancellationReason.PaymentFailed,
                                authorizedAt))
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void aRejectedFulfillmentCancelsAConfirmedOrderAndAsksForBothHoldsBack() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed)
                // When
                .when(handling(new RecordOrderShipmentFailure(orderId, OrderCancellationReason.CouldNotShip, sentAt)))
                // Then
                .then(
                        new OrderShipmentFailed(orderId, OrderCancellationReason.CouldNotShip, sentAt),
                        new OrderCanceled(
                                orderId,
                                paymentId,
                                OrderPaymentStatus.Authorized,
                                shipmentId,
                                OrderShipmentStatus.Reserved,
                                OrderCancellationReason.CouldNotShip,
                                sentAt));
    }

    // Paying again — the parcel left, the money did not come

    @Test
    public void retryingAPaymentThatFailedAfterTheParcelLeftAsksForANewPayment() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
                // Then
                .then(paymentRetried);
    }

    @Test
    public void retryingAPaymentThatHasNotFailedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
                // Then
                .thenNothing();
    }

    @Test
    public void retryingAnUnpaidOrderBeforeConfirmationAsksForANewPayment() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
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
                                OrderPaymentStatus.NotAuthorized,
                                null,
                                OrderShipmentStatus.NotReserved,
                                OrderCancellationReason.Requested,
                                retriedAt))
                // When
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
                // Then
                .thenNothing();
    }

    @Test
    public void theRetriedPaymentAuthorizedBeforeConfirmationConfirmsTheOrder() {
        // Given
        given(placed, stockReserved, unpaid, paymentRetried)
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, retriedAt)))
                // Then
                .thenNothing();
    }

    @Test
    public void aStockFailureWhileTheRetriedPaymentIsPendingWaitsForItsAnswer() {
        // Given
        given(placed, unpaid, paymentRetried)
                // When
                .when(handling(new RecordOrderShipmentFailure(
                        orderId, OrderCancellationReason.ProductWasOutOfStock, retriedAt)))
                // Then
                .then(new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, retriedAt));
    }

    @Test
    public void theRetriedPaymentAuthorizedAfterAStockFailureCancelsAndAsksForThatHoldBack() {
        // Given
        given(
                        placed,
                        unpaid,
                        paymentRetried,
                        new OrderShipmentFailed(orderId, OrderCancellationReason.ProductWasOutOfStock, retriedAt))
                // When
                .when(handling(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt)))
                // Then
                .then(
                        new OrderPaymentAuthorized(orderId, retriedPaymentId, retriedAt),
                        new OrderCanceled(
                                orderId,
                                retriedPaymentId,
                                OrderPaymentStatus.Authorized,
                                null,
                                OrderShipmentStatus.NotReserved,
                                OrderCancellationReason.ProductWasOutOfStock,
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
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
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
                .when(handling(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentAuthorization(orderId, paymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentCapture(orderId, retriedPaymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentFailure(orderId, retriedPaymentId, retriedAt)))
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
                .when(current -> new OrderDecider(failedAt -> dueAt.plusDays(14), () -> now)
                        .handle(new RecordOrderPaymentFailure(orderId, retriedPaymentId, retriedAt), current))
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
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentFailure(orderId, paymentId, retriedAt)))
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
                .when(handling(new RecordOrderPaymentCapture(orderId, paymentId, retriedAt)))
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
                .when(handling(new RetryOrderPayment(orderId), retriedAt))
                // Then
                .then(new OrderPaymentRetried(
                        orderId, PaymentId.derivedFrom(orderId.value(), 3), totalPrice, retriedAt));
    }

    @Test
    public void closingAnOrderStillUnpaidAfterTheParcelLeftEmitsOrderClosedUnpaid() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), closedAt))
                // Then
                .then(new OrderClosedUnpaid(orderId, closedAt));
    }

    @Test
    public void closingBeforeThePaymentIsDueEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue, latePaymentFailure)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), dueAt.minusMinutes(1)))
                // Then
                .thenNothing();
    }

    @Test
    public void closingAnUnpaidOrderBeforeConfirmationCancelsItAndAsksForTheStockBack() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), dueAt))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        OrderPaymentStatus.NotAuthorized,
                        shipmentId,
                        OrderShipmentStatus.Reserved,
                        OrderCancellationReason.PaymentFailed,
                        dueAt));
    }

    @Test
    public void closingAnUnpaidOrderBeforeConfirmationBeforeThePaymentIsDueEmitsNothing() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), dueAt.minusMinutes(1)))
                // Then
                .thenNothing();
    }

    @Test
    public void closingAnUnpaidOrderWhileItsRetriedPaymentIsPendingCancelsIt() {
        // Given
        given(placed, stockReserved, unpaid, paymentRetried)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), dueAt))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        retriedPaymentId,
                        OrderPaymentStatus.NotAuthorized,
                        shipmentId,
                        OrderShipmentStatus.Reserved,
                        OrderCancellationReason.PaymentFailed,
                        dueAt));
    }

    @Test
    public void cancelingAnUnpaidOrderAsksForTheStockBack() {
        // Given
        given(placed, stockReserved, unpaid)
                // When
                .when(handling(new CancelOrder(orderId, OrderCancellationReason.Requested), retriedAt))
                // Then
                .then(new OrderCanceled(
                        orderId,
                        paymentId,
                        OrderPaymentStatus.NotAuthorized,
                        shipmentId,
                        OrderShipmentStatus.Reserved,
                        OrderCancellationReason.Requested,
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
                .when(handling(new CloseUnpaidOrder(orderId), dueAt.minusMinutes(1)))
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
                .when(handling(new CloseUnpaidOrder(orderId), closedAt))
                // Then
                .then(new OrderClosedUnpaid(orderId, closedAt));
    }

    @Test
    public void closingAnOrderWhosePaymentHasNotFailedEmitsNothing() {
        // Given
        given(placed, paymentAuthorized, stockReserved, confirmed, packageSent, paymentDue)
                // When
                .when(handling(new CloseUnpaidOrder(orderId), closedAt))
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
                .when(handling(new CloseUnpaidOrder(orderId), closedAt))
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
                .when(handling(new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, closedAt)))
                // Then
                .thenNothing();
    }
}
