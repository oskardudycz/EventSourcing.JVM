package io.eventdriven.distributedprocesses.ecommerce;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService.carrier;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService.trackingNumber;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.PaymentTerms;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrder;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.CaptureFailureReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.CheckoutSession;
import io.eventdriven.distributedprocesses.ecommerce.payments.CreateCheckoutSession;
import io.eventdriven.distributedprocesses.ecommerce.payments.FailingFirstCapturePaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGatewayException;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.RefusingAfterAuthorizationPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.ThrowingPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestTracing;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ECommerceTests {
    private static final double unitPrice = 100;
    private static final Duration paymentTimeout = Duration.ofMinutes(15);
    private static final Duration authorizationValidity = Duration.ofDays(7);
    private static final Duration timeToPay = Duration.ofDays(14);
    private static final Duration checkoutSessionLifetime = Duration.ofMinutes(30);

    private final OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final OffsetDateTime captureBefore = now.plus(authorizationValidity);
    private final OffsetDateTime timedOutAt = now.plus(paymentTimeout);
    private final UUID customerId = UUID.randomUUID();
    private final ShoppingCartId cartId = ShoppingCartId.of(customerId, 1);
    private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
    private final double totalPrice = shoes.quantity() * unitPrice;

    private final OrderId orderId = OrderId.derivedFrom(cartId.value());
    private final String orderReference = orderId.value();
    private final PaymentId paymentId = PaymentId.derivedFrom(orderReference);
    private final PaymentId retriedPaymentId = PaymentId.derivedFrom(orderReference, 2);
    private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderReference);
    private final URI orderStatusPage =
            URI.create("http://localhost:8080/customers/%s/orders/%s".formatted(customerId, orderId.tail()));

    private final io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem[] orderItems = {
        new io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem(
                shoes.productId(), shoes.quantity(), unitPrice)
    };
    private final io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem[] shipmentItems = {
        new io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem(shoes.productId(), shoes.quantity())
    };

    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(TestTracing.untraced(), TestMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(
                    ShoppingCartsConfig.shoppingCartsRoutes(),
                    OrdersConfig.ordersRoutes(),
                    PaymentsConfig.paymentsRoutes(),
                    ShipmentsConfig.shipmentsRoutes()),
            List.of(
                    ShoppingCartsConfig.shoppingCartsExternalChannel(),
                    OrdersConfig.ordersInternalChannel(),
                    PaymentsConfig.paymentsRequestsChannel(),
                    PaymentsConfig.paymentsResponsesChannel(),
                    PaymentsConfig.paymentsInternalChannel(),
                    ShipmentsConfig.shipmentsRequestsChannel(),
                    ShipmentsConfig.shipmentsResponsesChannel(),
                    ShipmentsConfig.shipmentsInternalChannel()),
            List.of(
                    OrdersConfig.ordersInternalQueue(),
                    OrdersConfig.ordersShoppingCartsExternalQueue(),
                    OrdersConfig.ordersPaymentsResponsesQueue(),
                    OrdersConfig.ordersShipmentsResponsesQueue(),
                    PaymentsConfig.paymentsRequestsQueue(),
                    PaymentsConfig.paymentsInternalQueue(),
                    ShipmentsConfig.shipmentsRequestsQueue(),
                    ShipmentsConfig.shipmentsInternalQueue()));
    private final MessageCatcher messages = new MessageCatcher();
    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(now);
    private final CallingBackFulfillmentService fulfillmentService = new CallingBackFulfillmentService(messageBus);
    private ShoppingCartFacade shoppingCartFacade;
    private OrderFacade orderFacade;
    private PaymentFacade paymentFacade;
    private PendingOrders pendingOrders;
    private ScheduledUnpaidOrdersWorker unpaidOrdersWorker;
    private ScheduledPaymentTimeoutWorker paymentTimeoutWorker;

    @Test
    public void checkingOutTheCartPlacesTheOrderHoldsTheStockAndWaitsForTheCustomerToPay() {
        // Given
        var paymentGateway = new RecordingPaymentGateway(messageBus);
        configure(paymentGateway, true);
        addShoesToCart();

        // When
        checkOutCart();

        // Then
        messages.shouldReceiveMessages(
                cartCheckedOut(),
                placeOrder(),
                new AuthorizePayment(paymentId, orderReference, totalPrice),
                new ReserveStock(orderReference, shipmentItems),
                new ShipmentExternalEvent.StockReserved(shipmentId, orderReference, now),
                new RecordOrderStockReservation(orderId, shipmentId, now));
        assertThat(paymentGateway.calls).isEmpty();
    }

    @Test
    public void payingNowOpensACheckoutSessionAndTheWebhookConfirmsTheOrderAndRequestsFulfillment() {
        // Given
        var paymentGateway = new RecordingPaymentGateway(messageBus);
        configure(paymentGateway, true);
        addShoesToCart();
        checkOutCart();
        messages.reset();

        // When
        var session = payNow(paymentId);
        confirmByWebhook(paymentId);

        // Then
        var sessionExpiresAt = now.plus(checkoutSessionLifetime);
        assertThat(session).isEqualTo(new CheckoutSession(checkoutPageOf(paymentId), sessionExpiresAt));
        assertThat(paymentGateway.calls)
                .containsExactly("checkout-session:%s:%s:%s:%s:%s"
                        .formatted(paymentId, totalPrice, orderStatusPage, orderStatusPage, sessionExpiresAt));
        messages.shouldReceiveMessages(
                new ConfirmPaymentAuthorization(paymentId, captureBefore),
                new PaymentExternalEvent.PaymentAuthorized(orderReference, paymentId, now),
                new RecordOrderPaymentAuthorization(orderId, paymentId, now),
                new RequestFulfillment(shipmentId));
    }

    @Test
    public void payingNowAgainReusesTheOpenSessionAndOpensANewOneForTheSamePaymentOnceItExpired() {
        // Given
        var paymentGateway = new SilentPaymentGateway();
        configure(paymentGateway, true);
        addShoesToCart();
        checkOutCart();
        var first = payNow(paymentId);

        // When
        clock.set(now.plus(checkoutSessionLifetime).minusMinutes(1));
        var reused = payNow(paymentId);
        clock.set(now.plus(checkoutSessionLifetime));
        var renewed = payNow(paymentId);

        // Then
        assertThat(reused).isEqualTo(first);
        assertThat(renewed.expiresAt())
                .isEqualTo(now.plus(checkoutSessionLifetime).plus(checkoutSessionLifetime));
        assertThat(paymentGateway.checkoutSessionsCreated).containsExactly(paymentId, paymentId);
    }

    @Test
    public void theCustomerIsChargedOnlyAfterThePackageWasSentAndThatCompletesTheOrder() {
        // Given
        configure(new RecordingPaymentGateway(messageBus), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);

        // When
        fulfillmentService.sendPackages();

        // Then
        messages.shouldReceiveMessages(
                new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
                new ShipmentExternalEvent.PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
                new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
                new CapturePayment(paymentId),
                new ConfirmPaymentCapture(paymentId),
                new PaymentExternalEvent.PaymentCaptured(orderReference, paymentId, now),
                new RecordOrderPaymentCapture(orderId, paymentId, now));
        assertThat(pendingOrders.all()).extracting(PendingOrder::getOrderId).doesNotContain(orderId);
    }

    @Test
    public void theDeliveryIsRecordedByTheShipmentOnlyBecauseTheOrderAlreadyCompleted() {
        // Given
        configure(new RecordingPaymentGateway(messageBus), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);
        fulfillmentService.sendPackages();
        messages.reset();

        // When
        fulfillmentService.deliverPackages();

        // Then
        messages.shouldReceiveMessages(new DeliverPackage(shipmentId));
    }

    @Test
    public void productsOutOfStockWaitForThePaymentThenCancelTheOrderAndTheHoldWithoutCharging() {
        // Given
        var paymentGateway = new RecordingPaymentGateway(messageBus);
        configure(paymentGateway, false);
        addShoesToCart();
        checkOutCart();
        messages.reset();

        // When
        payNow(paymentId);
        confirmByWebhook(paymentId);

        // Then
        messages.shouldReceiveMessages(
                new ConfirmPaymentAuthorization(paymentId, captureBefore),
                new PaymentExternalEvent.PaymentAuthorized(orderReference, paymentId, now),
                new RecordOrderPaymentAuthorization(orderId, paymentId, now),
                new CancelPayment(paymentId));
        assertThat(paymentGateway.calls).contains("cancel:%s".formatted(paymentId));
    }

    @Test
    public void productsOutOfStockAreRecordedAtCheckoutWhileThePaymentIsStillPending() {
        // Given
        configure(new RecordingPaymentGateway(messageBus), false);
        addShoesToCart();

        // When
        checkOutCart();

        // Then
        messages.shouldReceiveMessages(
                cartCheckedOut(),
                placeOrder(),
                new AuthorizePayment(paymentId, orderReference, totalPrice),
                new ReserveStock(orderReference, shipmentItems),
                new ShipmentExternalEvent.ShipmentFailed(
                        shipmentId,
                        orderReference,
                        ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock,
                        now),
                new RecordOrderShipmentFailure(orderId, OrderCancellationReason.ProductWasOutOfStock, now));
    }

    @Test
    public void aRejectedFulfillmentCancelsTheOrderCancelsTheHoldAndReleasesTheStock() {
        // Given
        var paymentGateway = new RecordingPaymentGateway(messageBus);
        configure(paymentGateway, true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);

        // When
        fulfillmentService.rejectRequests();

        // Then
        messages.shouldReceiveMessages(
                new RejectFulfillment(shipmentId),
                new ShipmentExternalEvent.ShipmentFailed(
                        shipmentId,
                        orderReference,
                        ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected,
                        now),
                new RecordOrderShipmentFailure(orderId, OrderCancellationReason.CouldNotShip, now),
                new CancelPayment(paymentId),
                new ReleaseStock(shipmentId));
        assertThat(paymentGateway.calls).contains("cancel:%s".formatted(paymentId));
    }

    @Test
    public void aCustomerWhoNeverPaysLeavesTheOrderUnpaidWithTheStockStillHeld() {
        // Given
        var paymentGateway = new SilentPaymentGateway();
        configure(paymentGateway, true);
        addShoesToCart();
        checkOutCart();
        payNow(paymentId);
        messages.reset();

        // When
        clock.set(timedOutAt);
        paymentTimeoutWorker.handleAll();

        // Then
        messages.shouldReceiveMessages(
                new TimeOutPayment(paymentId, timedOutAt),
                new PaymentExternalEvent.PaymentFailed(orderReference, paymentId, timedOutAt),
                new RecordOrderPaymentFailure(orderId, paymentId, timedOutAt));
        assertThat(paymentGateway.expiredCheckoutSessions).containsExactly(paymentId);
    }

    @Test
    public void anAuthorizationArrivingAfterTheTimeoutChangesNothing() {
        // Given
        configure(new SilentPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        timeOutThePayment();

        // When
        confirmByWebhook(paymentId);

        // Then
        messages.shouldReceiveMessages(new ConfirmPaymentAuthorization(paymentId, captureBefore));
    }

    @Test
    public void retryingAnUnpaidOrderAndPayingTheNewPaymentConfirmsIt() {
        // Given
        configure(new RecordingPaymentGateway(messageBus), true);
        addShoesToCart();
        checkOutCart();
        timeOutThePayment();

        // When
        orderFacade.retryOrderPayment(new RetryOrderPayment(orderId));
        payNow(retriedPaymentId);
        confirmByWebhook(retriedPaymentId);

        // Then
        messages.shouldReceiveMessages(
                new AuthorizePayment(retriedPaymentId, orderReference, totalPrice),
                new ConfirmPaymentAuthorization(retriedPaymentId, captureBefore),
                new PaymentExternalEvent.PaymentAuthorized(orderReference, retriedPaymentId, timedOutAt),
                new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, timedOutAt),
                new RequestFulfillment(shipmentId));
    }

    @Test
    public void anOrderStillUnpaidBeforeConfirmationWhenThePaymentIsDueIsCanceledAndTheStockReleased() {
        // Given
        configure(new SilentPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        timeOutThePayment();

        // When
        clock.set(timedOutAt.plus(timeToPay));
        unpaidOrdersWorker.handleAll();

        // Then
        messages.shouldReceiveMessages(new CloseUnpaidOrder(orderId), new ReleaseStock(shipmentId));
    }

    @Test
    public void theCustomerCancelingAnUnpaidOrderReleasesTheStock() {
        // Given
        configure(new SilentPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        timeOutThePayment();

        // When
        orderFacade.cancelOrder(new CancelOrder(orderId, OrderCancellationReason.Requested));

        // Then
        messages.shouldReceiveMessages(new ReleaseStock(shipmentId));
    }

    @Test
    public void payingNowForAPaymentThatIsNotPendingAnyMoreIsRefused() {
        // Given
        configure(new RecordingPaymentGateway(messageBus), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);

        // When, Then
        assertThatThrownBy(() -> payNow(paymentId)).isInstanceOf(IllegalStateException.class);
        messages.shouldNotReceiveAnyEvent();
    }

    @Test
    public void aGatewayThatFailsToOpenTheSessionFailsTheRequestAndRecordsNothing() {
        // Given
        configure(new ThrowingPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        messages.reset();

        // When, Then
        assertThatThrownBy(() -> payNow(paymentId))
                .isInstanceOf(PaymentGatewayException.class)
                .hasRootCauseMessage("Payment provider is unreachable");
        messages.shouldNotReceiveAnyEvent();
    }

    @Test
    public void aFailedCaptureAfterThePackageWasSentWaitsForTheCustomerToPayAgain() {
        // Given
        configure(new RefusingAfterAuthorizationPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);

        // When
        fulfillmentService.sendPackages();

        // Then
        messages.shouldReceiveMessages(
                new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
                new ShipmentExternalEvent.PackageWasSent(shipmentId, orderReference, carrier, trackingNumber, now),
                new RecordOrderPackageSent(orderId, carrier, trackingNumber, now),
                new CapturePayment(paymentId),
                new FailPaymentCapture(paymentId, CaptureFailureReason.UnexpectedError),
                new PaymentExternalEvent.PaymentFailed(orderReference, paymentId, now),
                new RecordOrderPaymentFailure(orderId, paymentId, now));
        assertThat(pendingOrders.all()).extracting(PendingOrder::getOrderId).containsOnlyOnce(orderId);
    }

    @Test
    public void theCustomerPayingAgainAfterAFailedCaptureCompletesTheOrder() {
        // Given
        configure(new FailingFirstCapturePaymentGateway(messageBus), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);
        fulfillmentService.sendPackages();
        messages.reset();

        // When
        orderFacade.retryOrderPayment(new RetryOrderPayment(orderId));
        payNow(retriedPaymentId);
        confirmByWebhook(retriedPaymentId);

        // Then
        messages.shouldReceiveMessages(
                new AuthorizePayment(retriedPaymentId, orderReference, totalPrice),
                new ConfirmPaymentAuthorization(retriedPaymentId, captureBefore),
                new PaymentExternalEvent.PaymentAuthorized(orderReference, retriedPaymentId, now),
                new RecordOrderPaymentAuthorization(orderId, retriedPaymentId, now),
                new CapturePayment(retriedPaymentId),
                new ConfirmPaymentCapture(retriedPaymentId),
                new PaymentExternalEvent.PaymentCaptured(orderReference, retriedPaymentId, now),
                new RecordOrderPaymentCapture(orderId, retriedPaymentId, now));
        assertThat(pendingOrders.all()).extracting(PendingOrder::getOrderId).doesNotContain(orderId);
    }

    @Test
    public void anOrderStillUnpaidAfterShippingWhenThePaymentIsDueIsClosedUnpaid() {
        // Given
        configure(new RefusingAfterAuthorizationPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);
        fulfillmentService.sendPackages();
        messages.reset();

        // When
        clock.set(now.plus(timeToPay));
        unpaidOrdersWorker.handleAll();

        // Then
        messages.shouldReceiveMessages(new CloseUnpaidOrder(orderId));
        assertThat(pendingOrders.all()).extracting(PendingOrder::getOrderId).doesNotContain(orderId);
    }

    @Test
    public void anOrderIsNotClosedBeforeThePaymentIsDue() {
        // Given
        configure(new RefusingAfterAuthorizationPaymentGateway(), true);
        addShoesToCart();
        checkOutCart();
        pay(paymentId);
        fulfillmentService.sendPackages();
        messages.reset();

        // When
        clock.set(now.plus(timeToPay).minusMinutes(1));
        unpaidOrdersWorker.handleAll();

        // Then
        messages.shouldNotReceiveAnyEvent();
    }

    private ShoppingCartExternalEvent.ShoppingCartCheckedOut cartCheckedOut() {
        return new ShoppingCartExternalEvent.ShoppingCartCheckedOut(
                cartId, customerId, new PricedProductItem[] {new PricedProductItem(shoes, unitPrice)}, totalPrice, now);
    }

    private PlaceOrder placeOrder() {
        return new PlaceOrder(orderId, cartId, customerId, orderItems, totalPrice);
    }

    private CheckoutSession payNow(PaymentId paymentId) {
        return paymentFacade.createCheckoutSession(
                new CreateCheckoutSession(paymentId, orderReference, orderStatusPage, orderStatusPage));
    }

    private void confirmByWebhook(PaymentId paymentId) {
        messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore)));
    }

    private void pay(PaymentId paymentId) {
        payNow(paymentId);
        confirmByWebhook(paymentId);
        messages.reset();
    }

    private void timeOutThePayment() {
        clock.set(timedOutAt);
        paymentTimeoutWorker.handleAll();
        messages.reset();
    }

    private URI checkoutPageOf(PaymentId paymentId) {
        return URI.create("https://checkout.example/c/pay/" + paymentId.tail());
    }

    private void addShoesToCart() {
        shoppingCartFacade.addProductItem(customerId, shoes);
        messages.reset();
    }

    private void checkOutCart() {
        shoppingCartFacade.checkOut(new CheckOutShoppingCart(cartId, 0L));
    }

    private boolean isAboutThisPurchase(Object message) {
        return message.toString().contains(customerId.toString());
    }

    private void configure(PaymentGateway paymentGateway, boolean inStock) {
        messageBus.use(message -> {
            if (isAboutThisPurchase(message)) messages.catchMessage(message);
        });

        var shoppingCartsConfig = new ShoppingCartsConfig();
        var customerShoppingCarts =
                shoppingCartsConfig.customerShoppingCarts(eventStore, H2.bean(CustomerShoppingCartRepository.class));
        shoppingCartFacade = shoppingCartsConfig.shoppingCartFacade(
                eventStore,
                TestTracing.untraced(),
                customerShoppingCarts,
                item -> new PricedProductItem(item, unitPrice),
                clock::get);
        var shoppingCartSummaries =
                shoppingCartsConfig.shoppingCartSummaries(eventStore, H2.bean(ShoppingCartSummaryRepository.class));
        shoppingCartsConfig.shoppingCartExternalEventForwarder(eventStore, shoppingCartSummaries, messageBus);
        shoppingCartsConfig.carts(eventStore, H2.bean(CartRepository.class));

        var ordersConfig = new OrdersConfig();
        orderFacade = ordersConfig.orderFacade(
                eventStore, TestTracing.untraced(), messageBus, PaymentTerms.within(timeToPay), clock::get);
        pendingOrders = ordersConfig.pendingOrders(eventStore, H2.bean(PendingOrderRepository.class));
        var ordersAwaitingPayment =
                ordersConfig.ordersAwaitingPayment(eventStore, H2.bean(OrderAwaitingPaymentRepository.class));
        ordersConfig.customerOrders(eventStore, H2.bean(CustomerOrderRepository.class));
        ordersConfig.orderStatuses(eventStore, H2.bean(OrderStatusRepository.class));
        unpaidOrdersWorker = ordersConfig.unpaidOrdersWorker(ordersAwaitingPayment, messageBus, clock::get);

        var paymentsConfig = new PaymentsConfig();
        var paymentSummaries = paymentsConfig.paymentSummaries(eventStore, H2.bean(PaymentSummaryRepository.class));
        var pendingPayments = paymentsConfig.pendingPayments(eventStore, H2.bean(PendingPaymentRepository.class));
        var authorizedPayments =
                paymentsConfig.authorizedPayments(eventStore, H2.bean(AuthorizedPaymentRepository.class));
        paymentsConfig.paymentExternalEventForwarder(eventStore, paymentSummaries, messageBus);
        paymentsConfig.captureGatewayClient(eventStore, paymentGateway, messageBus);
        paymentsConfig.authorizationGatewayClient(eventStore, paymentGateway);
        paymentFacade = paymentsConfig.paymentFacade(
                eventStore,
                TestTracing.untraced(),
                messageBus,
                paymentGateway,
                PaymentValidity.of(paymentTimeout),
                clock::get);
        paymentTimeoutWorker = paymentsConfig.paymentTimeoutWorker(pendingPayments, messageBus, clock::get);
        paymentsConfig.authorizationExpiryWorker(authorizedPayments, messageBus, clock::get);

        var shipmentsConfig = new ShipmentsConfig();
        shipmentsConfig.shipmentFacade(eventStore, TestTracing.untraced(), messageBus, item -> inStock, clock::get);
        var shipmentSummaries = shipmentsConfig.shipmentSummaries(eventStore, H2.bean(ShipmentSummaryRepository.class));
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, shipmentSummaries, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, fulfillmentService);

        ordersConfig.subscribeOrderSaga(eventStore, messageBus);
    }
}
