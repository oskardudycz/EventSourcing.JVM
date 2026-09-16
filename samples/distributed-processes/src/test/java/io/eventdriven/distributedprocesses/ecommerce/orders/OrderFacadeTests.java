package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class OrderFacadeTests {
  private final OrderId orderId = OrderId.of(UUID.randomUUID());
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final PricedProductItem[] productItems = new PricedProductItem[]{
    new PricedProductItem(UUID.randomUUID(), 2, 12.5)
  };
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime authorizedAt = now.plusMinutes(1);
  private final OffsetDateTime reservedAt = now.plusMinutes(2);
  private final OffsetDateTime sentAt = now.plusMinutes(3);
  private final OffsetDateTime capturedAt = now.plusMinutes(4);
  private final OffsetDateTime deliveredAt = now.plusMinutes(5);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final MessageCatcher published = new MessageCatcher();

  public OrderFacadeTests() {
    eventStore.use(published::catchMessage);
    OrdersConfig.configure(commandBus, eventStore, eventStore, new InMemoryEventBus(), () -> now);
  }

  @Test
  public void initializeOrderDispatchesAndStoresOrderInitialized() {
    commandBus.send(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice));

    var initialized = new OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now);

    published.shouldReceiveMessages(initialized);
    assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(initialized));
  }

  @Test
  public void recordingBothHoldsDispatchesTheConfirmationInTheSameBatch() {
    initialize();

    commandBus.send(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt));
    commandBus.send(new RecordOrderStockReservation(orderId, shipmentId, reservedAt));

    published.shouldReceiveMessages(
      new OrderPaymentAuthorized(orderId, paymentId, authorizedAt),
      new OrderStockReserved(orderId, shipmentId, reservedAt),
      new OrderConfirmed(orderId, shipmentId, reservedAt)
    );
  }

  @Test
  public void recordOrderPackageSentDispatchesOrderPackageSentCarryingThePayment() {
    confirm();

    commandBus.send(new RecordOrderPackageSent(orderId, sentAt));

    published.shouldReceiveMessages(new OrderPackageSent(orderId, paymentId, sentAt));
  }

  @Test
  public void recordOrderPaymentCaptureDispatchesOrderPaymentCapturedForTheOrderTotal() {
    confirm();
    commandBus.send(new RecordOrderPackageSent(orderId, sentAt));
    published.reset();

    commandBus.send(new RecordOrderPaymentCapture(orderId, capturedAt));

    published.shouldReceiveMessages(
      new OrderPaymentCaptured(orderId, paymentId, totalPrice, capturedAt)
    );
  }

  @Test
  public void recordOrderDeliveryAfterTheCaptureCompletesTheOrder() {
    confirm();
    commandBus.send(
      new RecordOrderPackageSent(orderId, sentAt),
      new RecordOrderPaymentCapture(orderId, capturedAt)
    );
    published.reset();

    commandBus.send(new RecordOrderDelivery(orderId, deliveredAt));

    published.shouldReceiveMessages(
      new OrderShipmentDelivered(orderId, shipmentId, deliveredAt),
      new OrderCompleted(orderId, deliveredAt)
    );
  }

  @Test
  public void recordOrderPaymentFailureWithStockReservedCancelsAndSaysTheStockIsStillHeld() {
    initialize();
    commandBus.send(new RecordOrderStockReservation(orderId, shipmentId, reservedAt));
    published.reset();

    commandBus.send(new RecordOrderPaymentFailure(orderId, authorizedAt));

    published.shouldReceiveMessages(
      new OrderPaymentFailed(orderId, authorizedAt),
      new OrderCancelled(
        orderId,
        null,
        OrderPaymentState.NotAuthorized,
        shipmentId,
        OrderShipmentState.Reserved,
        OrderCancellationReason.PaymentFailed,
        authorizedAt
      )
    );
  }

  @Test
  public void recordOrderShipmentFailureWithTheCardAuthorizedCancelsAndSaysTheHoldIsStillThere() {
    initialize();
    commandBus.send(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt));
    published.reset();

    commandBus.send(new RecordOrderShipmentFailure(orderId, reservedAt));

    published.shouldReceiveMessages(
      new OrderShipmentFailed(orderId, reservedAt),
      new OrderCancelled(
        orderId,
        paymentId,
        OrderPaymentState.Authorized,
        null,
        OrderShipmentState.NotReserved,
        OrderCancellationReason.ProductWasOutOfStock,
        reservedAt
      )
    );
  }

  @Test
  public void repeatingARecordedHoldStoresNothingAndDoesNotThrow() {
    initialize();
    commandBus.send(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt));
    published.reset();

    assertThatCode(() -> commandBus.send(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)
    )).doesNotThrowAnyException();

    published.shouldNotReceiveAnyEvent();
    assertThat(storedEvents()).hasSize(2);
  }

  @Test
  public void cancelOrderDispatchesOrderCancelledWithWhereBothParticipantsStood() {
    confirm();

    commandBus.send(new CancelOrder(orderId, OrderCancellationReason.Requested));

    published.shouldReceiveMessages(new OrderCancelled(
      orderId,
      paymentId,
      OrderPaymentState.Authorized,
      shipmentId,
      OrderShipmentState.Reserved,
      OrderCancellationReason.Requested,
      now
    ));
  }

  @Test
  public void mapsIdToItsOwnStream() {
    assertThat(OrderFacade.mapToStreamId(orderId))
      .isEqualTo("Order-%s".formatted(orderId.tail()));
  }

  private void initialize() {
    commandBus.send(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice));
    published.reset();
  }

  private void confirm() {
    initialize();
    commandBus.send(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    );
    published.reset();
  }

  private List<Object> storedEvents() {
    return switch (eventStore.read(OrderFacade.mapToStreamId(orderId))) {
      case EventStore.ReadResult.Success success -> List.of(success.events());
      default -> List.of();
    };
  }
}
