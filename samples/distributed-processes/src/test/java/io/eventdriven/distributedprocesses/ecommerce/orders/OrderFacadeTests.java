package io.eventdriven.distributedprocesses.ecommerce.orders;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class OrderFacadeTests {
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
    private final Duration timeToPay = Duration.ofDays(14);

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(TestTracing.untraced());
    private final MessageCatcher published = new MessageCatcher();
    private final AtomicReference<OffsetDateTime> clock = new AtomicReference<>(now);
    private final OrderFacade orders;

    public OrderFacadeTests() {
        eventStore.use(published::catchMessage);
        orders = OrdersConfig.configure(
                        eventStore,
                        messageBus,
                        PaymentTerms.within(timeToPay),
                        H2.bean(PendingOrderRepository.class),
                        H2.bean(OrderAwaitingPaymentRepository.class),
                        H2.bean(CustomerOrderRepository.class),
                        H2.bean(OrderStatusRepository.class),
                        clock::get)
                .facade();
    }

    @Test
    public void placeOrderDispatchesAndStoresOrderPlaced() {
        messageBus.publish(List.of(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice)));

        var placed = new OrderPlaced(orderId, cartId, customerId, productItems, totalPrice, now);

        published.shouldReceiveMessages(placed);
        assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(placed));
    }

    @Test
    public void recordingBothHoldsDispatchesTheConfirmationInTheSameBatch() {
        place();

        messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));
        messageBus.publish(List.of(new RecordOrderStockReservation(orderId, shipmentId, reservedAt)));

        published.shouldReceiveMessages(
                new OrderPaymentAuthorized(orderId, paymentId, authorizedAt),
                new OrderStockReserved(orderId, shipmentId, reservedAt),
                new OrderConfirmed(orderId, paymentId, shipmentId, reservedAt));
    }

    @Test
    public void recordOrderPackageSentDispatchesOrderPackageSentAndThePaymentDue() {
        confirm();

        messageBus.publish(List.of(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)));

        published.shouldReceiveMessages(
                new OrderPackageSent(orderId, carrier, trackingNumber, sentAt),
                new OrderPaymentDue(orderId, paymentId, sentAt));
    }

    @Test
    public void recordOrderPaymentCaptureCompletesTheOrder() {
        send();

        messageBus.publish(List.of(new RecordOrderPaymentCapture(orderId, capturedAt)));

        published.shouldReceiveMessages(
                new OrderPaymentCaptured(orderId, paymentId, totalPrice, capturedAt),
                new OrderCompleted(orderId, capturedAt));
    }

    @Test
    public void recordOrderPaymentCaptureBeforeTheDispatchStoresNothingAndDoesNotThrow() {
        confirm();

        assertThatCode(() -> messageBus.publish(List.of(new RecordOrderPaymentCapture(orderId, capturedAt))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void recordOrderPaymentFailureBeforeConfirmationKeepsTheOrderOpenWithItsStock() {
        place();
        messageBus.publish(List.of(new RecordOrderStockReservation(orderId, shipmentId, reservedAt)));
        published.reset();

        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, authorizedAt)));

        published.shouldReceiveMessages(new OrderPaymentFailed(orderId, authorizedAt, authorizedAt.plus(timeToPay)));
    }

    @Test
    public void customerCommandsAreNotTakenFromTheMessageBus() {
        place();
        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, authorizedAt)));
        published.reset();

        messageBus.publish(
                List.of(new RetryOrderPayment(orderId), new CancelOrder(orderId, OrderCancellationReason.Requested)));

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void recordOrderShipmentFailureWithTheCardAuthorizedCancelsAndSaysTheHoldIsStillThere() {
        place();
        messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));
        published.reset();

        messageBus.publish(List.of(
                new RecordOrderShipmentFailure(orderId, OrderCancellationReason.ProductWasOutOfStock, reservedAt)));

        published.shouldReceiveMessages(
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
    public void recordOrderPaymentFailureAfterTheDispatchKeepsTheOrderOpenForTheTimeThePaymentTermsGive() {
        send();

        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, capturedAt)));

        published.shouldReceiveMessages(new OrderPaymentFailed(orderId, capturedAt, capturedAt.plus(timeToPay)));
    }

    @Test
    public void cancelOrderAfterTheDispatchStoresNothingAndDoesNotThrow() {
        send();

        assertThatCode(() -> orders.cancelOrder(new CancelOrder(orderId, OrderCancellationReason.Requested)))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void repeatingARecordedHoldStoresNothingAndDoesNotThrow() {
        place();
        messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));
        published.reset();

        assertThatCode(() -> messageBus.publish(
                        List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).hasSize(2);
    }

    @Test
    public void cancelOrderDispatchesOrderCanceledWithWhereBothParticipantsStood() {
        place();
        messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));
        published.reset();

        orders.cancelOrder(new CancelOrder(orderId, OrderCancellationReason.Requested));

        published.shouldReceiveMessages(new OrderCanceled(
                orderId,
                paymentId,
                OrderPaymentStatus.Authorized,
                null,
                OrderShipmentStatus.NotReserved,
                OrderCancellationReason.Requested,
                now));
    }

    @Test
    public void cancelOrderAfterConfirmationStoresNothingAndDoesNotThrow() {
        confirm();

        assertThatCode(() -> orders.cancelOrder(new CancelOrder(orderId, OrderCancellationReason.Requested)))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void retryOrderPaymentAfterAFailedCaptureDispatchesOrderPaymentRetried() {
        send();
        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, capturedAt)));
        published.reset();

        orders.retryOrderPayment(new RetryOrderPayment(orderId));

        published.shouldReceiveMessages(
                new OrderPaymentRetried(orderId, PaymentId.derivedFrom(orderId.value(), 2), totalPrice, now));
    }

    @Test
    public void closeUnpaidOrderAfterAFailedCaptureDispatchesOrderClosedUnpaidOnceThePaymentIsDue() {
        send();
        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, capturedAt)));
        published.reset();
        clock.set(capturedAt.plus(timeToPay));

        messageBus.publish(List.of(new CloseUnpaidOrder(orderId)));

        published.shouldReceiveMessages(new OrderClosedUnpaid(orderId, capturedAt.plus(timeToPay)));
    }

    @Test
    public void closeUnpaidOrderBeforeThePaymentIsDueStoresNothingAndDoesNotThrow() {
        send();
        messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, capturedAt)));
        published.reset();
        clock.set(capturedAt.plus(timeToPay).minusMinutes(1));

        assertThatCode(() -> messageBus.publish(List.of(new CloseUnpaidOrder(orderId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void mapsIdToItsOwnStream() {
        assertThat(OrderFacade.mapToStreamId(orderId)).isEqualTo("Order-%s".formatted(orderId.tail()));
    }

    private void place() {
        messageBus.publish(List.of(new PlaceOrder(orderId, cartId, customerId, productItems, totalPrice)));
        published.reset();
    }

    private void confirm() {
        place();
        messageBus.publish(List.of(
                new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
                new RecordOrderStockReservation(orderId, shipmentId, reservedAt)));
        published.reset();
    }

    private void send() {
        confirm();
        messageBus.publish(List.of(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)));
        published.reset();
    }

    private List<Object> storedEvents() {
        return switch (eventStore.read(OrderFacade.mapToStreamId(orderId))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }
}
