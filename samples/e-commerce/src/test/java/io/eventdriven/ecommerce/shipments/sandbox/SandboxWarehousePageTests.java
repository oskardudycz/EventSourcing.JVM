package io.eventdriven.ecommerce.shipments.sandbox;

import static io.eventdriven.ecommerce.orders.OrderRequests.getOrderStatus;
import static io.eventdriven.ecommerce.orders.OrderRequests.payNow;
import static io.eventdriven.ecommerce.shipments.ShipmentRequests.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.addProductItem;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.checkOut;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SEE_OTHER;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import io.eventdriven.ecommerce.shop.DemoProducts;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.api.ApiE2ESpecification;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The sandbox warehouse page, where a person plays the warehouse: the stock and the packages to send. */
@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SandboxWarehousePageTests extends ApiE2ESpecification {
    private final UUID customerId = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();
    private final Order.Id orderId =
            Order.Id.derivedFrom(ShoppingCart.Id.of(customerId, 1).value());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());

    private final TestStep[] placedOrder = {addProductItem(customerId, product, 2), checkOut(customerId, weakETag(0))};
    private final TestStep[] paidOrder = scenario(
            placedOrder,
            payNow(customerId, orderId, paymentId),
            publish(new ConfirmPaymentAuthorization(
                    paymentId, OffsetDateTime.now().plusDays(7))));

    @Nested
    public class Stock {
        @Test
        public void thePageListsEveryDemoProductWithItsStock() {
            given().when(warehousePage())
                    .then(expectResponse(
                            OK,
                            htmlContaining(DemoProducts.all().stream()
                                    .map(DemoProducts.Product::name)
                                    .toArray(String[]::new)),
                            htmlContaining("In stock", "Out of stock")));
        }

        @Test
        public void theLimitedEditionIsSoldOut_soItsOrderIsOutOfStock() {
            given(addProductItem(customerId, DemoProducts.soldOut().id(), 1), checkOut(customerId, weakETag(0)))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("progress", "OutOfStock"))));
        }

        @Test
        public void markingAProductOutOfStock_returnsToTheWarehouse() {
            given().when(markInStock(product, false)).then(expectResponse(SEE_OTHER, redirectsTo(warehouse)));
        }

        @Test
        public void aProductMarkedOutOfStock_leavesTheNextOrderOfItOutOfStock() {
            given(scenario(new TestStep[] {markInStock(product, false)}, placedOrder))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("progress", "OutOfStock"))));
        }
    }

    @Nested
    public class Packages {
        @Test
        public void aPaidOrdersPackageWaitsOnThePageToBeSentOrRejected() {
            given(paidOrder)
                    .when(warehousePage())
                    .then(expectResponse(
                            OK, htmlContaining(packageAction(orderId, "send"), packageAction(orderId, "reject"))));
        }

        @Test
        public void aPaidOrderIsProcessingUntilThePackageIsSent() {
            given(paidOrder)
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("progress", "Processing"))));
        }

        @Test
        public void sendingThePackage_returnsToTheWarehouse() {
            given(paidOrder).when(sendPackage(orderId)).then(expectResponse(SEE_OTHER, redirectsTo(warehouse)));
        }

        @Test
        public void aSentPackageCompletesTheOrder() {
            given(scenario(paidOrder, sendPackage(orderId)))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("progress", "Completed", "carrier", "Sandbox Express"))));
        }

        @Test
        public void aSentPackageCanBeDeliveredButNotSentAgain() {
            given(scenario(paidOrder, sendPackage(orderId)))
                    .when(warehousePage())
                    .then(expectResponse(
                            OK,
                            htmlContaining(packageAction(orderId, "deliver")),
                            htmlNotContaining(packageAction(orderId, "send"))));
        }

        @Test
        public void aDeliveredPackageOffersNothingMore() {
            given(scenario(paidOrder, sendPackage(orderId), deliverPackage(orderId)))
                    .when(warehousePage())
                    .then(expectResponse(
                            OK, htmlNotContaining(packageAction(orderId, "deliver"), packageAction(orderId, "send"))));
        }

        @Test
        public void aRejectedPackageCancelsTheOrder() {
            given(scenario(paidOrder, rejectPackage(orderId)))
                    .when(getOrderStatus(customerId, orderId))
                    .then(expectResponse(OK, body(Map.of("progress", "Canceled"))));
        }

        @Test
        public void aPackageThatTheWarehouseDoesNotHoldIsNotFound() {
            given().when(sendPackage(orderId)).then(expectResponse(NOT_FOUND));
        }
    }
}
