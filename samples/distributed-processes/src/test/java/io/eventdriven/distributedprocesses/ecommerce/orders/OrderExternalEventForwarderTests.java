package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.ecommerce.orders.external.OrderExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;

public class OrderExternalEventForwarderTests {
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
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public OrderExternalEventForwarderTests() {
    integrationEventBus.use(externalEvents::catchMessage);

    OrdersConfig.configure(commandBus, eventStore, eventStore, integrationEventBus, () -> now);
  }

  @Test
  public void initializingForwardsOrderInitializedWithItsItemsAndTheCartItCameFrom() {
    commandBus.send(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice));

    externalEvents.shouldReceiveMessages(new OrderExternalEvent.OrderInitialized(
      orderId, cartId, clientId, productItems, totalPrice, now
    ));
  }

  @Test
  public void confirmingForwardsOrderConfirmedWithTheShipmentToSend() {
    initialize();

    commandBus.send(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    );

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderConfirmed(orderId, paymentId, reservedAt)
    );
  }

  @Test
  public void recordingTheCaptureForwardsOrderPaymentCapturedWithTheShipmentToSend() {
    confirm();

    commandBus.send(new RecordOrderPaymentCapture(orderId, capturedAt));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderPaymentCaptured(orderId, shipmentId, totalPrice, capturedAt)
    );
  }

  @Test
  public void recordingTheDispatchForwardsNothingBecauseNobodyActsOnIt() {
    confirm();
    commandBus.send(new RecordOrderPaymentCapture(orderId, capturedAt));
    externalEvents.reset();

    commandBus.send(new RecordOrderPackageSent(orderId, sentAt));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void completingForwardsOrderCompleted() {
    confirm();
    commandBus.send(
      new RecordOrderPaymentCapture(orderId, capturedAt),
      new RecordOrderPackageSent(orderId, sentAt)
    );
    externalEvents.reset();

    commandBus.send(new RecordOrderDelivery(orderId, deliveredAt));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderCompleted(orderId, deliveredAt)
    );
  }

  @Test
  public void cancellingForwardsOrderCancelledWithWhereBothParticipantsStood() {
    confirm();

    commandBus.send(new CancelOrder(orderId, OrderCancellationReason.Requested));

    externalEvents.shouldReceiveMessages(new OrderExternalEvent.OrderCancelled(
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
  public void aSingleHoldForwardsNothingBecauseNobodyOutsideTheOrderActsOnIt() {
    initialize();

    commandBus.send(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void initialize() {
    commandBus.send(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice));
    externalEvents.reset();
  }

  private void confirm() {
    initialize();
    commandBus.send(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    );
    externalEvents.reset();
  }
}
