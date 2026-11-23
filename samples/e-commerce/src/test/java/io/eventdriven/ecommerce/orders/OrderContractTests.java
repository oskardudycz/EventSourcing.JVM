package io.eventdriven.ecommerce.orders;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.ecommerce.orders.OrderCommand.*;
import io.eventdriven.ecommerce.orders.OrderEvent.*;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.MessageSnapshot;
import io.eventdriven.strictland.SnapshotVariant;
import io.eventdriven.testing.contracts.Contracts;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class OrderContractTests {
    private static final UUID customerId = uuid(1);
    private static final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);
    private static final Order.Id orderId = Order.Id.of(uuid(2));
    private static final Payment.Id paymentId = Payment.Id.of(uuid(3));
    private static final Shipment.Id shipmentId = Shipment.Id.of(uuid(4));
    private static final Order.PricedProductItem[] productItems = {new Order.PricedProductItem(uuid(5), 2, 64.995)};

    @Nested
    class StoredEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new OrderPlaced(orderId, cartId, customerId, productItems, 129.99, at),
                    new OrderPaymentAuthorized(orderId, paymentId, at),
                    new OrderStockReserved(orderId, shipmentId, at),
                    new OrderConfirmed(orderId, paymentId, shipmentId, at),
                    new OrderPaymentCaptured(orderId, paymentId, 129.99, at),
                    new OrderPackageSent(orderId, "DHL", "TRACK-1", at),
                    new OrderPaymentDue(orderId, paymentId, at.plusDays(14)),
                    new OrderPaymentFailed(orderId, at, at.plusDays(14)),
                    new OrderShipmentFailed(orderId, Order.CancellationReason.ProductWasOutOfStock, at),
                    new OrderPaymentRetried(orderId, paymentId, 129.99, at),
                    new OrderClosedUnpaid(orderId, at),
                    new OrderCompleted(orderId, at),
                    new OrderCanceled(
                            orderId,
                            paymentId,
                            Order.PaymentStatus.Authorized,
                            shipmentId,
                            Order.ShipmentStatus.Reserved,
                            Order.CancellationReason.Requested,
                            at));
        }

        static Stream<Named<Object>> examplesWithRequiredData() {
            return Contracts.examples(new OrderCanceled(
                    orderId,
                    paymentId,
                    Order.PaymentStatus.NotAuthorized,
                    null,
                    Order.ShipmentStatus.NotReserved,
                    Order.CancellationReason.Requested,
                    at));
        }

        @ParameterizedTest
        @MethodSource("examplesWithRequiredData")
        void keepsItsFormatWithOnlyTheRequiredData(Object event) {
            MessageContract.specification(options)
                    .given(event)
                    .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
                    .thenContractIsUnchanged();
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }

        @Test
        void anOrderCanceledBeforeTheReservationIsReadWithoutAShipment() {
            MessageContract.specification(options)
                    .given(MessageSnapshot.of(OrderCanceled.class).variant("WithRequiredData"))
                    .whenDeserializedAs(OrderCanceled.class)
                    .thenBackwardCompatible(
                            event -> assertThat(event.shipmentId()).isNull());
        }
    }

    @Nested
    class CommandOnTheBus {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new PlaceOrder(orderId, cartId, customerId, productItems, 129.99, at),
                    new RecordOrderPaymentAuthorization(orderId, paymentId, at),
                    new RecordOrderStockReservation(orderId, shipmentId, at),
                    new RecordOrderPackageSent(orderId, "DHL", "TRACK-1", at),
                    new RecordOrderPaymentCapture(orderId, paymentId, at),
                    new RecordOrderPaymentFailure(orderId, paymentId, at, at.plusDays(14)),
                    new RecordOrderShipmentFailure(orderId, Order.CancellationReason.ProductWasOutOfStock, at),
                    new CloseUnpaidOrder(orderId, at));
        }

        static Stream<Named<Object>> examplesWithRequiredData() {
            return Contracts.examples(
                    new PlaceOrder(orderId, cartId, customerId, productItems, 129.99),
                    new RecordOrderPaymentFailure(orderId, paymentId, at),
                    new CloseUnpaidOrder(orderId));
        }

        @ParameterizedTest
        @MethodSource("examplesWithRequiredData")
        void keepsItsFormatWithOnlyTheRequiredData(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
                    .thenContractIsUnchanged();
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerialized()
                    .thenContractIsUnchanged();
        }
    }
}
