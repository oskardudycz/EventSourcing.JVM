package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderRequests.*;
import static io.eventdriven.ecommerce.shipments.Warehouse.sendsThePackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.*;

import io.eventdriven.ecommerce.OutsideWorld;
import io.eventdriven.ecommerce.Time;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import io.eventdriven.ecommerce.payments.PaymentProvider;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ReleaseStock;
import io.eventdriven.ecommerce.shipments.Warehouse;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.api.ApiE2ESpecification;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OutsideWorld.class)
public class OrdersApiTests extends ApiE2ESpecification {
    private static final double unitPrice = 100;
    private static final Duration checkoutSessionLifetime = Duration.ofMinutes(30);
    private static final Duration authorizationValidity = Duration.ofDays(7);

    @Autowired
    private Time time;

    @Autowired
    private PaymentProvider paymentProvider;

    @LocalServerPort
    private int port;

    private final UUID customerId = UUID.randomUUID();
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final double totalPrice = shoes.quantity() * unitPrice;
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);
    private final Order.Id orderId = Order.Id.derivedFrom(cartId.value());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());
    private final Payment.Id retriedPaymentId = Payment.Id.derivedFrom(orderId.value(), 2);
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderId.value());

    private TestStep[] placedOrder;
    private TestStep[] orderWithFailedPayment;
    private TestStep[] orderWithOpenCheckout;
    private TestStep[] paidOrder;
    private TestStep[] completedOrder;

    @BeforeEach
    public void describeTheOrders() {
        placedOrder = new TestStep[] {publish(checkedOut(customerId, cartId))};
        orderWithFailedPayment =
                scenario(placedOrder, publish(new PaymentFailed(orderId.value(), paymentId, time.now())));
        orderWithOpenCheckout = scenario(placedOrder, payNow(customerId, orderId, paymentId));
        paidOrder = scenario(
                orderWithOpenCheckout,
                publish(new ConfirmPaymentAuthorization(paymentId, time.now().plus(authorizationValidity))));
        completedOrder = scenario(paidOrder, sendsThePackage(shipmentId));
    }

    @Nested
    public class CancellingOrder {
        @Test
        public void cancelOrder_succeeds_withAcceptedAndReleasesTheStock_forUnpaidOrder() {
            given(placedOrder)
                    .when(cancelOrder(customerId, orderId))
                    .then(expectResponse(ACCEPTED), expectPublished(new ReleaseStock(shipmentId)));
        }

        @Test
        public void cancelOrder_succeeds_withAcceptedButChangesNothing_forCompletedOrder() {
            given(completedOrder)
                    .when(cancelOrder(customerId, orderId))
                    .then(expectResponse(ACCEPTED), expectNothingPublished());
        }

        @Test
        public void cancelOrder_fails_withNotFound_forUnknownOrder() {
            given().when(cancelOrder(customerId, orderId)).then(expectError(NOT_FOUND));
        }
    }

    @Nested
    public class RetryingOrderPayment {
        @Test
        public void retryOrderPayment_succeeds_withANewPaymentToAuthorize_afterThePaymentFailed() {
            given(orderWithFailedPayment)
                    .when(retryOrderPayment(customerId, orderId))
                    .then(
                            expectResponse(ACCEPTED),
                            expectPublished(new AuthorizePayment(retriedPaymentId, orderId.value(), totalPrice)));
        }

        @Test
        public void retryOrderPayment_succeeds_withAcceptedButChangesNothing_forPendingPayment() {
            given(placedOrder)
                    .when(retryOrderPayment(customerId, orderId))
                    .then(expectResponse(ACCEPTED), expectNothingPublished());
        }

        @Test
        public void retryOrderPayment_fails_withNotFound_forUnknownOrder() {
            given().when(retryOrderPayment(customerId, orderId)).then(expectError(NOT_FOUND));
        }
    }

    @Nested
    public class PayingNow {
        @Test
        public void payNow_succeeds_withSeeOtherToTheProvidersPage_forASessionThatTheProviderOpened() {
            var orderStatusPage = URI.create(
                    "http://localhost:%d/shop/customers/%s/orders/%s".formatted(port, customerId, orderId.tail()));

            given(placedOrder)
                    .when(payNow(customerId, orderId, paymentId))
                    .then(
                            expectResponse(
                                    SEE_OTHER,
                                    headers(Map.of(
                                            HttpHeaders.LOCATION,
                                            PaymentProvider.checkoutPageOf(paymentId)
                                                    .toString()))),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .containsExactly("checkout-session:%s:%s:%s:%s"
                                            .formatted(
                                                    totalPrice,
                                                    orderStatusPage,
                                                    orderStatusPage,
                                                    time.now().plus(checkoutSessionLifetime))));
        }

        @Test
        public void payNow_succeeds_withTheSameSession_whileItIsOpen() {
            given(orderWithOpenCheckout, time.passes(checkoutSessionLifetime.minusMinutes(1)))
                    .when(payNow(customerId, orderId, paymentId))
                    .then(
                            expectResponse(SEE_OTHER),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .hasSize(1));
        }

        @Test
        public void payNow_succeeds_withANewSessionForTheSamePayment_onceTheOpenOneExpired() {
            given(orderWithOpenCheckout, time.passes(checkoutSessionLifetime))
                    .when(payNow(customerId, orderId, paymentId))
                    .then(
                            expectResponse(SEE_OTHER),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .hasSize(2));
        }

        @Test
        public void payNow_fails_withConflict_forPaidPayment() {
            given(paidOrder)
                    .when(payNow(customerId, orderId, paymentId))
                    .then(expectError(CONFLICT), expectNothingPublished());
        }

        @Test
        public void payNow_fails_withBadGatewayAndRecordsNothing_whenTheProviderIsUnreachable() {
            given(placedOrder, paymentProvider.isUnreachableFor(paymentId))
                    .when(payNow(customerId, orderId, paymentId))
                    .then(expectError(BAD_GATEWAY), expectNothingPublished());
        }

        @Test
        public void payNow_fails_withNotFound_forPaymentOfAnotherOrder() {
            var otherCustomerId = UUID.randomUUID();
            var otherCartId = ShoppingCart.Id.of(otherCustomerId, 1);
            var otherPaymentId = Payment.Id.derivedFrom(
                    Order.Id.derivedFrom(otherCartId.value()).value());

            given(placedOrder, publish(checkedOut(otherCustomerId, otherCartId)))
                    .when(payNow(customerId, orderId, otherPaymentId))
                    .then(expectError(NOT_FOUND));
        }

        @Test
        public void payNow_fails_withNotFound_forUnknownOrder() {
            given().when(payNow(customerId, orderId, paymentId)).then(expectError(NOT_FOUND));
        }
    }

    @Nested
    public class GettingCustomerOrders {
        @Test
        public void getCustomerOrders_succeeds_withTheNewestOrderFirst() {
            var secondCartId = ShoppingCart.Id.of(customerId, 2);

            given(completedOrder, time.passes(Duration.ofMinutes(1)), publish(checkedOut(customerId, secondCartId)))
                    .when(getOrders(customerId))
                    .then(expectResponse(
                            OK,
                            body(List.of(
                                    Map.of(
                                            "orderId",
                                            Order.Id.derivedFrom(secondCartId.value())
                                                    .value(),
                                            "progress",
                                            "AwaitingPayment"),
                                    Map.of("orderId", orderId.value(), "progress", "Completed")))));
        }

        @Test
        public void getCustomerOrders_succeeds_withNoOrders_forCustomerWhoNeverCheckedOut() {
            given().when(getOrders(customerId)).then(expectResponse(OK, body(List.of())));
        }
    }

    @Nested
    public class GettingOrderStatus {
        @Test
        public void getOrderStatus_succeeds_withPayNowAndCancel_forUnpaidOrder() {
            given(placedOrder)
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(
                            OK,
                            body(Map.of(
                                    "progress",
                                    "AwaitingPayment",
                                    "paymentId",
                                    paymentId.value(),
                                    "actions",
                                    List.of("PayNow", "CancelOrder")))));
        }

        @Test
        public void getOrderStatus_succeeds_withPayNowStill_afterTheProviderFailedToOpenTheSession() {
            given(
                            placedOrder,
                            paymentProvider.isUnreachableFor(paymentId),
                            attempt(payNow(customerId, orderId, paymentId)))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("actions", List.of("PayNow", "CancelOrder")))));
        }

        @Test
        public void getOrderStatus_succeeds_withANewPaymentToPay_afterRetryingAFailedPayment() {
            given(orderWithFailedPayment, retryOrderPayment(customerId, orderId))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(
                            OK,
                            body(Map.of(
                                    "progress",
                                    "AwaitingPayment",
                                    "paymentId",
                                    retriedPaymentId.value(),
                                    "actions",
                                    List.of("PayNow", "CancelOrder")))));
        }

        @Test
        public void getOrderStatus_succeeds_withTheCarrierAndNoActions_forCompletedOrder() {
            given(completedOrder)
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(
                            OK,
                            body(Map.of(
                                    "progress",
                                    "Completed",
                                    "carrier",
                                    Warehouse.carrier,
                                    "trackingNumber",
                                    Warehouse.trackingNumber,
                                    "actions",
                                    List.of()))));
        }

        @Test
        public void getOrderStatus_fails_withNotFound_forUnknownOrder() {
            given().when(getOrderStatus(customerId, orderId)).then(expectError(NOT_FOUND));
        }

        @Test
        public void getOrderStatus_fails_withNotFound_forAnotherCustomersOrder() {
            var otherCustomerId = UUID.randomUUID();
            var otherCartId = ShoppingCart.Id.of(otherCustomerId, 1);

            given(publish(checkedOut(otherCustomerId, otherCartId)))
                    .when(getOrderStatus(customerId, Order.Id.derivedFrom(otherCartId.value())))
                    .then(expectError(NOT_FOUND));
        }
    }

    private ShoppingCartCheckedOut checkedOut(UUID customerId, ShoppingCart.Id cartId) {
        return new ShoppingCartCheckedOut(
                cartId,
                customerId,
                new PricedProductItem[] {new PricedProductItem(shoes, unitPrice)},
                totalPrice,
                time.now());
    }
}
