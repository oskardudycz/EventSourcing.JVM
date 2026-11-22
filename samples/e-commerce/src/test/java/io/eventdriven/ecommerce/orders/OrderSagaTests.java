package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.*;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentCommand;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class OrderSagaTests {
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
    private final Order.Id orderId = Order.Id.derivedFrom(cartId.value());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderId.value());
    private final UUID customerId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final double totalPrice = 25;
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";
    private final OffsetDateTime now = OffsetDateTime.now();

    private final Order.PricedProductItem[] orderItems =
            new Order.PricedProductItem[] {new Order.PricedProductItem(productId, 2, 12.5)};

    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(new MessageRoutes().routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), "published")),
            List.of(new MessageChannel("published")),
            List.of());
    private final MessageCatcher sent = new MessageCatcher();
    private final OrderSaga saga = new OrderSaga(messageBus);

    public OrderSagaTests() {
        messageBus.use(envelope -> sent.catchMessage(envelope.message()));
    }

    // Hold phase

    @Test
    public void aCheckedOutCartStartsAnOrderWhoseIdIsDerivedFromTheCart() {
        saga.onShoppingCartCheckedOut(
                new ShoppingCartExternalEvent.ShoppingCartCheckedOut(cartId, customerId, cartItems(), totalPrice, now));

        sent.shouldReceiveMessages(new PlaceOrder(orderId, cartId, customerId, orderItems, totalPrice));
    }

    @Test
    public void aPlacedOrderTakesBothHoldsTogether() {
        saga.onOrderPlaced(orderPlaced());

        sent.shouldReceiveMessages(
                new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), totalPrice),
                new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems()));
    }

    @Test
    public void anAuthorizedPaymentIsRecordedAgainstTheOrderTheReferenceNames() {
        saga.onPaymentAuthorized(new PaymentExternalEvent.PaymentAuthorized(orderId.value(), paymentId, now));

        sent.shouldReceiveMessages(new RecordOrderPaymentAuthorization(orderId, paymentId, now));
    }

    @Test
    public void reservedStockIsRecordedAgainstTheOrder() {
        saga.onStockReserved(new ShipmentExternalEvent.StockReserved(shipmentId, orderId.value(), now));

        sent.shouldReceiveMessages(new RecordOrderStockReservation(orderId, shipmentId, now));
    }

    @Test
    public void handlingTheSameEventTwiceSendsTwoIdenticalCommands() {
        saga.onOrderPlaced(orderPlaced());
        saga.onOrderPlaced(orderPlaced());

        var authorizePayment = new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), totalPrice);
        var reserveStock = new ShipmentCommand.ReserveStock(orderId.value(), shipmentItems());

        sent.shouldReceiveMessages(authorizePayment, reserveStock, authorizePayment, reserveStock);
    }

    // Commit phase

    @Test
    public void aConfirmedOrderSendsThePackageBeforeAnyMoneyMoves() {
        saga.onOrderConfirmed(new OrderEvent.OrderConfirmed(orderId, paymentId, shipmentId, now));

        sent.shouldReceiveMessages(new ShipmentCommand.RequestFulfillment(shipmentId));
    }

    @Test
    public void aSentPackageIsRecordedAgainstTheOrder() {
        saga.onPackageWasSent(
                new ShipmentExternalEvent.PackageWasSent(shipmentId, orderId.value(), carrier, trackingNumber, now));

        sent.shouldReceiveMessages(new RecordOrderPackageSent(orderId, carrier, trackingNumber, now));
    }

    @Test
    public void theOrdersPaymentDueEventIsWhatCapturesThePayment() {
        saga.onOrderPaymentDue(new OrderEvent.OrderPaymentDue(orderId, paymentId, now));

        sent.shouldReceiveMessages(new PaymentCommand.CapturePayment(paymentId));
    }

    @Test
    public void aCapturedPaymentIsRecordedAgainstTheOrder() {
        saga.onPaymentCaptured(new PaymentExternalEvent.PaymentCaptured(orderId.value(), paymentId, now));

        sent.shouldReceiveMessages(new RecordOrderPaymentCapture(orderId, paymentId, now));
    }

    // Compensation

    @Test
    public void aFailedPaymentIsRecordedRatherThanCancellingTheOrderDirectly() {
        saga.onPaymentFailed(new PaymentExternalEvent.PaymentFailed(orderId.value(), paymentId, now));

        sent.shouldReceiveMessages(new RecordOrderPaymentFailure(orderId, paymentId, now));
    }

    @Test
    public void aPaymentFailurePassesThePaymentIdOfTheRetriedPayment() {
        var retriedPaymentId = Payment.Id.derivedFrom(orderId.value(), 2);

        saga.onPaymentFailed(new PaymentExternalEvent.PaymentFailed(orderId.value(), retriedPaymentId, now));

        sent.shouldReceiveMessages(new RecordOrderPaymentFailure(orderId, retriedPaymentId, now));
    }

    @Test
    public void anOutOfStockProductIsRecordedRatherThanCancellingTheOrderDirectly() {
        saga.onShipmentFailed(new ShipmentExternalEvent.ShipmentFailed(
                shipmentId, orderId.value(), ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock, now));

        sent.shouldReceiveMessages(
                new RecordOrderShipmentFailure(orderId, Order.CancellationReason.ProductWasOutOfStock, now));
    }

    @Test
    public void aRejectedFulfillmentIsRecordedAsCouldNotShip() {
        saga.onShipmentFailed(new ShipmentExternalEvent.ShipmentFailed(
                shipmentId, orderId.value(), ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected, now));

        sent.shouldReceiveMessages(new RecordOrderShipmentFailure(orderId, Order.CancellationReason.CouldNotShip, now));
    }

    // Paying again

    @Test
    public void aRetriedPaymentAsksForTheNewPaymentToBeAuthorized() {
        var retriedPaymentId = Payment.Id.derivedFrom(orderId.value(), 2);

        saga.onOrderPaymentRetried(new OrderEvent.OrderPaymentRetried(orderId, retriedPaymentId, totalPrice, now));

        sent.shouldReceiveMessages(new PaymentCommand.AuthorizePayment(retriedPaymentId, orderId.value(), totalPrice));
    }

    @Test
    public void aCanceledOrderWithAnAuthorizedHoldCancelsItAndReleasesTheStock() {
        saga.onOrderCanceled(orderCanceled(Order.PaymentStatus.Authorized, Order.ShipmentStatus.Reserved));

        sent.shouldReceiveMessages(
                new PaymentCommand.CancelPayment(paymentId), new ShipmentCommand.ReleaseStock(shipmentId));
    }

    @Test
    public void aCanceledOrderWithOnlyTheCardHeldCancelsThatHold() {
        saga.onOrderCanceled(orderCanceled(Order.PaymentStatus.Authorized, Order.ShipmentStatus.NotReserved));

        sent.shouldReceiveMessages(new PaymentCommand.CancelPayment(paymentId));
    }

    @Test
    public void aCanceledOrderThatTookNoHoldsUndoesNothing() {
        saga.onOrderCanceled(orderCanceled(Order.PaymentStatus.NotAuthorized, Order.ShipmentStatus.NotReserved));

        sent.shouldNotReceiveAnyEvent();
    }

    private OrderEvent.OrderPlaced orderPlaced() {
        return new OrderEvent.OrderPlaced(orderId, cartId, customerId, orderItems, totalPrice, now);
    }

    private OrderEvent.OrderCanceled orderCanceled(
            Order.PaymentStatus paymentStatus, Order.ShipmentStatus shipmentStatus) {
        return new OrderEvent.OrderCanceled(
                orderId, paymentId, paymentStatus, shipmentId, shipmentStatus, Order.CancellationReason.Requested, now);
    }

    private Shipment.ProductItem[] shipmentItems() {
        return new Shipment.ProductItem[] {new Shipment.ProductItem(productId, 2)};
    }

    private io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem[] cartItems() {
        return new io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem[] {
            new io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem(
                    new io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem(productId, 2), 12.5)
        };
    }
}
