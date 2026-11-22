package io.eventdriven.ecommerce;

import static io.eventdriven.ecommerce.orders.OrderRequests.*;
import static io.eventdriven.ecommerce.shipments.Warehouse.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.addProductItem;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.checkOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.SEE_OTHER;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderCommand.*;
import io.eventdriven.ecommerce.orders.OrderEvent;
import io.eventdriven.ecommerce.orders.OrderFacade;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.ecommerce.payments.PaymentEvent;
import io.eventdriven.ecommerce.payments.PaymentProvider;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentAuthorized;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentCaptured;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.ecommerce.shipments.Warehouse;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.PackageWasSent;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.ShipmentFailed;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.StockReserved;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ChangeablePrices;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.api.ApiE2ESpecification;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class PurchaseTests extends ApiE2ESpecification {
    private static final double unitPrice = ChangeablePrices.defaultUnitPrice;
    private static final Duration paymentValidity = Duration.ofHours(24);
    private static final Duration timeToPay = Duration.ofDays(7);
    private static final Duration authorizationValidity = Duration.ofDays(7);

    @Autowired
    private Time time;

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private Warehouse warehouse;

    private final UUID customerId = UUID.randomUUID();
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final double totalPrice = shoes.quantity() * unitPrice;
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);
    private final Order.Id orderId = Order.Id.derivedFrom(cartId.value());
    private final String orderReference = orderId.value();
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderReference);
    private final Payment.Id retriedPaymentId = Payment.Id.derivedFrom(orderReference, 2);
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderReference);
    private final Shipment.ProductItem[] shipmentItems = {new Shipment.ProductItem(shoes.productId(), shoes.quantity())
    };

    private OffsetDateTime now;
    private TestStep[] cartWithShoes;
    private TestStep[] checkedOutCart;
    private TestStep[] orderWithOpenCheckout;
    private TestStep[] paidOrder;
    private TestStep[] sentOrder;
    private TestStep[] orderWithTimedOutPayment;

    @BeforeEach
    public void describeThePurchase() {
        now = time.now();
        cartWithShoes = new TestStep[] {addProductItem(customerId, shoes.productId(), shoes.quantity())};
        checkedOutCart = scenario(cartWithShoes, checkOut(customerId, weakETag(0)));
        orderWithOpenCheckout = scenario(checkedOutCart, payNow(customerId, orderId, paymentId));
        paidOrder = scenario(orderWithOpenCheckout, providerConfirms(paymentId));
        sentOrder = scenario(paidOrder, sendsThePackage(shipmentId));
        orderWithTimedOutPayment = scenario(checkedOutCart, time.passes(paymentValidity));
    }

    @Test
    public void checkingOutTheCartPlacesTheOrderHoldsTheStockAndWaitsForTheCustomerToPay() {
        given(cartWithShoes)
                .when(checkOut(customerId, weakETag(0)))
                .then(
                        expectResponse(ACCEPTED),
                        expectPublished(
                                cartCheckedOut(),
                                placeOrder(),
                                new AuthorizePayment(paymentId, orderReference, totalPrice),
                                new ReserveStock(orderReference, shipmentItems),
                                new StockReserved(shipmentId, orderReference, now),
                                new RecordOrderStockReservation(orderId, shipmentId, now)),
                        expectTrace(span(
                                "http post /customers/{customerId}/shopping-carts/current/checkout",
                                handled("CheckOutShoppingCart"),
                                span(
                                        "handle ShoppingCartCheckedOut",
                                        span(
                                                "handle PlaceOrder",
                                                span(
                                                        "command.handle",
                                                        Map.of(
                                                                "eventdriven.command.type", "PlaceOrder",
                                                                "eventdriven.command.status", "success",
                                                                "eventdriven.command.event_types", "OrderPlaced",
                                                                "eventdriven.stream.name",
                                                                        OrderFacade.mapToStreamId(orderId),
                                                                "eventdriven.stream.version.after", "0"),
                                                        span(
                                                                "eventStore.appendToStream",
                                                                Map.of(
                                                                        "eventdriven.eventstore.append.status",
                                                                        "success"))),
                                                span(
                                                        "handle OrderPlaced",
                                                        span("handle AuthorizePayment", handled("AuthorizePayment")),
                                                        span("handle ReserveStock", handled("ReserveStock"))))))),
                        expectCorrelatedTrace(),
                        expectCausation(published(
                                cartCheckedOut(),
                                sent(
                                        placeOrder(),
                                        appended(
                                                orderPlaced(),
                                                sent(
                                                        new AuthorizePayment(paymentId, orderReference, totalPrice),
                                                        appended(new PaymentEvent.PaymentAuthorizationRequested(
                                                                paymentId,
                                                                orderReference,
                                                                totalPrice,
                                                                now,
                                                                now.plus(paymentValidity)))),
                                                sent(
                                                        new ReserveStock(orderReference, shipmentItems),
                                                        appended(
                                                                new ShipmentEvent.StockReserved(
                                                                        shipmentId, orderReference, shipmentItems, now),
                                                                published(
                                                                        new StockReserved(
                                                                                shipmentId, orderReference, now),
                                                                        sent(new RecordOrderStockReservation(
                                                                                orderId, shipmentId, now))))))))),
                        expectMetric("eventdriven.command.handle", Map.of("eventdriven.command.type", "PlaceOrder"), 1),
                        expectAppended("OrderPlaced", 1),
                        expectAppended("PaymentAuthorizationRequested", 1),
                        expectAppended("StockReserved", 1),
                        outcome ->
                                assertThat(paymentProvider.callsFor(paymentId)).isEmpty());
    }

    @Test
    public void payingNowOpensACheckoutSessionAtTheProviderAndPublishesNothing() {
        given(checkedOutCart)
                .when(payNow(customerId, orderId, paymentId))
                .then(
                        expectResponse(SEE_OTHER),
                        expectNothingPublished(),
                        outcome ->
                                assertThat(paymentProvider.callsFor(paymentId)).hasSize(1));
    }

    @Test
    public void theProvidersConfirmationConfirmsTheOrderAndRequestsFulfillmentInThePayNowTrace() {
        given(orderWithOpenCheckout)
                .when(providerConfirms(paymentId))
                .then(
                        expectPublished(
                                new ConfirmPaymentAuthorization(paymentId, now.plus(authorizationValidity)),
                                new PaymentAuthorized(orderReference, paymentId, now),
                                new RecordOrderPaymentAuthorization(orderId, paymentId, now),
                                new RequestFulfillment(shipmentId)),
                        expectTrace(span(
                                "http post /customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session",
                                span(
                                        "handle ConfirmPaymentAuthorization",
                                        handled("ConfirmPaymentAuthorization"),
                                        span("handle RequestFulfillment", handled("RequestFulfillment"))))),
                        expectCorrelatedTrace(),
                        expectAppended("PaymentAuthorized", 1),
                        expectAppended("OrderConfirmed", 1),
                        outcome ->
                                assertThat(warehouse.wasAskedToSend(shipmentId)).isTrue());
    }

    @Test
    public void theCustomerIsChargedOnlyAfterThePackageWasSentAndThatCompletesTheOrder() {
        given(paidOrder)
                .when(sendsThePackage(shipmentId))
                .then(
                        expectPublished(
                                new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
                                new PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
                                new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
                                new CapturePayment(paymentId),
                                new ConfirmPaymentCapture(paymentId),
                                new PaymentCaptured(orderReference, paymentId, now),
                                new RecordOrderPaymentCapture(orderId, paymentId, now)),
                        expectTrace(span(
                                "http post /customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session",
                                span(
                                        "handle ConfirmPackageSent",
                                        span("handle CapturePayment", handled("CapturePayment"))))),
                        expectCorrelatedTrace(),
                        expectAppended("PaymentCaptured", 1),
                        expectAppended("OrderCompleted", 1),
                        outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                .contains("capture:%s".formatted(totalPrice)));
    }

    @Test
    public void theDeliveryIsRecordedByTheShipmentOnlyBecauseTheOrderAlreadyCompleted() {
        given(sentOrder).when(deliversThePackage(shipmentId)).then(expectPublished(new DeliverPackage(shipmentId)));
    }

    @Test
    public void productsOutOfStockAreRecordedAtCheckoutWhileThePaymentIsStillPending() {
        given(warehouse.isOutOf(shoes.productId()), addProductItem(customerId, shoes.productId(), shoes.quantity()))
                .when(checkOut(customerId, weakETag(0)))
                .then(
                        expectResponse(ACCEPTED),
                        expectPublished(
                                cartCheckedOut(),
                                placeOrder(),
                                new AuthorizePayment(paymentId, orderReference, totalPrice),
                                new ReserveStock(orderReference, shipmentItems),
                                new ShipmentFailed(
                                        shipmentId, orderReference, ShipmentFailed.Reason.ProductsOutOfStock, now),
                                new RecordOrderShipmentFailure(
                                        orderId, Order.CancellationReason.ProductWasOutOfStock, now)));
    }

    @Test
    public void productsOutOfStockWaitForThePaymentThenCancelTheOrderAndTheHoldWithoutCharging() {
        given(
                        warehouse.isOutOf(shoes.productId()),
                        addProductItem(customerId, shoes.productId(), shoes.quantity()),
                        checkOut(customerId, weakETag(0)),
                        payNow(customerId, orderId, paymentId))
                .when(providerConfirms(paymentId))
                .then(
                        expectPublished(
                                new ConfirmPaymentAuthorization(paymentId, now.plus(authorizationValidity)),
                                new PaymentAuthorized(orderReference, paymentId, now),
                                new RecordOrderPaymentAuthorization(orderId, paymentId, now),
                                new CancelPayment(paymentId)),
                        outcome ->
                                assertThat(paymentProvider.callsFor(paymentId)).contains("cancel"));
    }

    @Test
    public void aRejectedFulfillmentCancelsTheOrderCancelsTheHoldAndReleasesTheStock() {
        given(paidOrder)
                .when(rejectsTheRequest(shipmentId))
                .then(
                        expectPublished(
                                new RejectFulfillment(shipmentId),
                                new ShipmentFailed(
                                        shipmentId, orderReference, ShipmentFailed.Reason.FulfillmentRejected, now),
                                new RecordOrderShipmentFailure(orderId, Order.CancellationReason.CouldNotShip, now),
                                new CancelPayment(paymentId),
                                new ReleaseStock(shipmentId)),
                        expectCorrelatedTrace(),
                        expectAppended("FulfillmentRejected", 1),
                        expectAppended("OrderCanceled", 1),
                        expectAppended("PaymentCanceled", 1),
                        expectAppended("StockReleased", 1),
                        outcome ->
                                assertThat(paymentProvider.callsFor(paymentId)).contains("cancel"));
    }

    @Test
    public void aCustomerWhoNeverPaysLeavesTheOrderUnpaidWithTheStockStillHeld() {
        var timedOutAt = now.plus(paymentValidity);

        given(orderWithOpenCheckout)
                .when(time.passes(paymentValidity))
                .then(
                        expectPublished(
                                new TimeOutPayment(paymentId, timedOutAt),
                                new PaymentFailed(orderReference, paymentId, timedOutAt),
                                new RecordOrderPaymentFailure(orderId, paymentId, timedOutAt)),
                        expectTrace(span(
                                "http post /customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session",
                                span(
                                        "handle TimeOutPayment",
                                        handled("TimeOutPayment"),
                                        span(
                                                "handle RecordOrderPaymentFailure",
                                                handled("RecordOrderPaymentFailure"))))),
                        expectCorrelatedTrace(),
                        expectAppended("PaymentTimedOut", 1),
                        expectAppended("OrderPaymentFailed", 1),
                        outcome ->
                                assertThat(paymentProvider.callsFor(paymentId)).contains("expire-checkout-session"));
    }

    @Test
    public void anAuthorizationArrivingAfterTheTimeoutChangesNothing() {
        given(orderWithTimedOutPayment)
                .when(providerConfirms(paymentId))
                .then(expectPublished(new ConfirmPaymentAuthorization(paymentId, now.plus(authorizationValidity))));
    }

    @Test
    public void retryingAnUnpaidOrderAndPayingTheNewPaymentConfirmsIt() {
        var paidAgainAt = now.plus(paymentValidity);

        given(
                        orderWithTimedOutPayment,
                        retryOrderPayment(customerId, orderId),
                        payNow(customerId, orderId, retriedPaymentId))
                .when(providerConfirms(retriedPaymentId))
                .then(expectPublished(
                        new ConfirmPaymentAuthorization(retriedPaymentId, now.plus(authorizationValidity)),
                        new PaymentAuthorized(orderReference, retriedPaymentId, paidAgainAt),
                        new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, paidAgainAt),
                        new RequestFulfillment(shipmentId)));
    }

    @Test
    public void anOrderStillUnpaidBeforeConfirmationWhenThePaymentIsDueIsCanceledAndTheStockReleased() {
        given(orderWithTimedOutPayment)
                .when(time.passes(timeToPay))
                .then(expectPublished(new CloseUnpaidOrder(orderId), new ReleaseStock(shipmentId)));
    }

    @Test
    public void theCustomerCancelingAnUnpaidOrderReleasesTheStock() {
        given(orderWithTimedOutPayment)
                .when(cancelOrder(customerId, orderId))
                .then(expectResponse(ACCEPTED), expectPublished(new ReleaseStock(shipmentId)));
    }

    @Test
    public void aFailedCaptureAfterThePackageWasSentWaitsForTheCustomerToPayAgain() {
        given(paidOrder, paymentProvider.refusesEverythingAfterAuthorizing(paymentId))
                .when(sendsThePackage(shipmentId))
                .then(
                        expectPublished(
                                new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
                                new PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
                                new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
                                new CapturePayment(paymentId),
                                new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError),
                                new PaymentFailed(orderReference, paymentId, now),
                                new RecordOrderPaymentFailure(orderId, paymentId, now)),
                        expectCorrelatedTrace(),
                        expectAppended("PaymentCaptureFailed", 1),
                        expectAppended("OrderPaymentFailed", 1));
    }

    @Test
    public void theCustomerPayingAgainAfterAFailedCaptureCompletesTheOrder() {
        given(
                        scenario(
                                paidOrder,
                                paymentProvider.refusesTheFirstCaptureOf(paymentId),
                                sendsThePackage(shipmentId)),
                        retryOrderPayment(customerId, orderId),
                        payNow(customerId, orderId, retriedPaymentId))
                .when(providerConfirms(retriedPaymentId))
                .then(expectPublished(
                        new ConfirmPaymentAuthorization(retriedPaymentId, now.plus(authorizationValidity)),
                        new PaymentAuthorized(orderReference, retriedPaymentId, now),
                        new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, now),
                        new CapturePayment(retriedPaymentId),
                        new ConfirmPaymentCapture(retriedPaymentId),
                        new PaymentCaptured(orderReference, retriedPaymentId, now),
                        new RecordOrderPaymentCapture(orderId, retriedPaymentId, now)));
    }

    @Test
    public void anOrderStillUnpaidAfterShippingWhenThePaymentIsDueIsClosedUnpaid() {
        given(paidOrder, paymentProvider.refusesEverythingAfterAuthorizing(paymentId), sendsThePackage(shipmentId))
                .when(time.passes(timeToPay))
                .then(expectPublished(new CloseUnpaidOrder(orderId)));
    }

    @Test
    public void aPaymentWhoseCaptureFailedDoesNotTimeOutLater() {
        given(paidOrder, paymentProvider.refusesEverythingAfterAuthorizing(paymentId), sendsThePackage(shipmentId))
                .when(time.passes(paymentValidity))
                .then(expectNothingPublished());
    }

    @Test
    public void anOrderIsNotClosedBeforeThePaymentIsDue() {
        given(paidOrder, paymentProvider.refusesEverythingAfterAuthorizing(paymentId), sendsThePackage(shipmentId))
                .when(time.passes(timeToPay.minusMinutes(1)))
                .then(expectNothingPublished());
    }

    private static ExpectedSpan handled(String commandType) {
        return span(
                "command.handle",
                Map.of("eventdriven.command.type", commandType, "eventdriven.command.status", "success"));
    }

    private static Expectation expectAppended(String eventType, double count) {
        return expectMetric("eventdriven.event.appending.count", Map.of("eventdriven.event.type", eventType), count);
    }

    @Override
    protected boolean isAbout(Object message) {
        return message.toString().contains(customerId.toString());
    }

    private TestStep providerConfirms(Payment.Id paymentId) {
        return publish(new ConfirmPaymentAuthorization(paymentId, now.plus(authorizationValidity)));
    }

    private ShoppingCartCheckedOut cartCheckedOut() {
        return new ShoppingCartCheckedOut(
                cartId, customerId, new PricedProductItem[] {new PricedProductItem(shoes, unitPrice)}, totalPrice, now);
    }

    private OrderEvent.OrderPlaced orderPlaced() {
        return new OrderEvent.OrderPlaced(
                orderId,
                cartId,
                customerId,
                new Order.PricedProductItem[] {
                    new Order.PricedProductItem(shoes.productId(), shoes.quantity(), unitPrice)
                },
                totalPrice,
                now);
    }

    private PlaceOrder placeOrder() {
        return new PlaceOrder(
                orderId,
                cartId,
                customerId,
                new Order.PricedProductItem[] {
                    new Order.PricedProductItem(shoes.productId(), shoes.quantity(), unitPrice)
                },
                totalPrice);
    }
}
