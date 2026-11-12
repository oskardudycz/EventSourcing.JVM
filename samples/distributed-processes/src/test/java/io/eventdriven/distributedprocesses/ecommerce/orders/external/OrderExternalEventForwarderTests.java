package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  private final String carrier = "DHL";
  private final String trackingNumber = "JD014600006281234567";
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime authorizedAt = now.plusMinutes(1);
  private final OffsetDateTime reservedAt = now.plusMinutes(2);
  private final OffsetDateTime sentAt = now.plusMinutes(3);
  private final OffsetDateTime capturedAt = now.plusMinutes(4);
  private final OffsetDateTime deliveredAt = now.plusMinutes(5);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public OrderExternalEventForwarderTests() {
    messageBus.use(message -> {
      if (message instanceof OrderExternalEvent)
        externalEvents.catchMessage(message);
    });

    OrdersConfig.configure(eventStore, messageBus, () -> now);
  }

  @Test
  public void initializingForwardsNothingBecauseNothingIsPromisedToTheCustomerYet() {
    messageBus.publish(List.of(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void confirmingForwardsOrderConfirmedWithWhatTheCustomerBought() {
    initialize();

    messageBus.publish(List.of(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    ));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderConfirmed(
        orderId, clientId, productItems, totalPrice, reservedAt
      )
    );
  }

  @Test
  public void recordingTheDispatchForwardsOrderShippedForTheCustomerToTrack() {
    confirm();

    messageBus.publish(List.of(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderShipped(
        orderId, clientId, shipmentId, carrier, trackingNumber, sentAt
      )
    );
  }

  @Test
  public void recordingTheCaptureForwardsNothingBecauseTheOrderIsNotDoneYet() {
    confirm();
    messageBus.publish(List.of(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt)));
    externalEvents.reset();

    messageBus.publish(List.of(new RecordOrderPaymentCapture(orderId, capturedAt)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void completingForwardsOrderCompletedWithWhatTheCustomerPaidFor() {
    confirm();
    messageBus.publish(List.of(
      new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt),
      new RecordOrderPaymentCapture(orderId, capturedAt)
    ));
    externalEvents.reset();

    messageBus.publish(List.of(new RecordOrderDelivery(orderId, deliveredAt)));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderCompleted(
        orderId, clientId, productItems, totalPrice, deliveredAt
      )
    );
  }

  @Test
  public void cancellingForwardsOrderCancelledWithThePublicReason() {
    confirm();

    messageBus.publish(List.of(new CancelOrder(orderId, OrderCancellationReason.Requested)));

    externalEvents.shouldReceiveMessages(new OrderExternalEvent.OrderCancelled(
      orderId,
      clientId,
      productItems,
      totalPrice,
      OrderExternalEvent.OrderCancelled.Reason.Requested,
      now
    ));
  }

  @Test
  public void runningOutOfStockForwardsOrderCancelledBecauseOfTheMissingProducts() {
    initialize();
    messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));
    externalEvents.reset();

    messageBus.publish(List.of(new RecordOrderShipmentFailure(orderId, reservedAt)));

    externalEvents.shouldReceiveMessages(new OrderExternalEvent.OrderCancelled(
      orderId,
      clientId,
      productItems,
      totalPrice,
      OrderExternalEvent.OrderCancelled.Reason.ProductsOutOfStock,
      reservedAt
    ));
  }

  @Test
  public void aFailedPaymentForwardsOrderCancelledBecauseThePaymentFailed() {
    initialize();
    messageBus.publish(List.of(new RecordOrderStockReservation(orderId, shipmentId, reservedAt)));
    externalEvents.reset();

    messageBus.publish(List.of(new RecordOrderPaymentFailure(orderId, authorizedAt)));

    externalEvents.shouldReceiveMessages(new OrderExternalEvent.OrderCancelled(
      orderId,
      clientId,
      productItems,
      totalPrice,
      OrderExternalEvent.OrderCancelled.Reason.PaymentFailed,
      authorizedAt
    ));
  }

  @Test
  public void forwarderRefusesASummaryThatHasNotSeenTheEventSoTheStoreDeliversItAgain() {
    var summaries = new OrderSummaries(new Database());
    var bus = new InMemoryMessageBus();
    var published = new MessageCatcher();
    bus.use(published::catchMessage);
    var forwarder = new OrderExternalEventForwarder(summaries, bus);
    var streamId = OrderFacade.mapToStreamId(orderId);

    summaries.onInitialized(new EventEnvelope<>(
      new OrderEvent.OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now),
      new EventMetadata(streamId, 0, 0)
    ));
    var confirmed = new EventEnvelope<>(
      new OrderEvent.OrderConfirmed(orderId, paymentId, shipmentId, reservedAt),
      new EventMetadata(streamId, 1, 1)
    );

    assertThatThrownBy(() -> forwarder.onConfirmed(confirmed)).isInstanceOf(IllegalStateException.class);
    published.shouldNotReceiveAnyEvent();

    summaries.onConfirmed(confirmed);
    forwarder.onConfirmed(confirmed);

    published.shouldReceiveMessages(
      new OrderExternalEvent.OrderConfirmed(orderId, clientId, productItems, totalPrice, reservedAt)
    );
  }

  @Test
  public void aSingleHoldForwardsNothingBecauseNobodyOutsideTheOrderActsOnIt() {
    initialize();

    messageBus.publish(List.of(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt)));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  private void initialize() {
    messageBus.publish(List.of(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice)));
    externalEvents.reset();
  }

  private void confirm() {
    initialize();
    messageBus.publish(List.of(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    ));
    externalEvents.reset();
  }
}
