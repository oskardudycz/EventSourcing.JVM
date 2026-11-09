package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryCommandBus;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.Order;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.*;
import static org.assertj.core.api.Assertions.assertThat;

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
  private final InMemoryCommandBus commandBus = new InMemoryCommandBus();
  private final InMemoryEventBus integrationEventBus = new InMemoryEventBus();
  private final MessageCatcher externalEvents = new MessageCatcher();

  public OrderExternalEventForwarderTests() {
    integrationEventBus.use(externalEvents::catchMessage);

    OrdersConfig.configure(commandBus, eventStore, eventStore, integrationEventBus, () -> now);
  }

  @Test
  public void initializingForwardsNothingBecauseNothingIsPromisedToTheCustomerYet() {
    commandBus.send(new InitializeOrder(orderId, cartId, clientId, productItems, totalPrice));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void confirmingForwardsOrderConfirmedWithWhatTheCustomerBought() {
    initialize();

    commandBus.send(
      new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt),
      new RecordOrderStockReservation(orderId, shipmentId, reservedAt)
    );

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderConfirmed(
        orderId, clientId, productItems, totalPrice, reservedAt
      )
    );
  }

  @Test
  public void recordingTheDispatchForwardsOrderShippedForTheCustomerToTrack() {
    confirm();

    commandBus.send(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderShipped(
        orderId, clientId, shipmentId, carrier, trackingNumber, sentAt
      )
    );
  }

  @Test
  public void recordingTheCaptureForwardsNothingBecauseTheOrderIsNotDoneYet() {
    confirm();
    commandBus.send(new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt));
    externalEvents.reset();

    commandBus.send(new RecordOrderPaymentCapture(orderId, capturedAt));

    externalEvents.shouldNotReceiveAnyEvent();
  }

  @Test
  public void completingForwardsOrderCompletedWithWhatTheCustomerPaidFor() {
    confirm();
    commandBus.send(
      new RecordOrderPackageSent(orderId, carrier, trackingNumber, sentAt),
      new RecordOrderPaymentCapture(orderId, capturedAt)
    );
    externalEvents.reset();

    commandBus.send(new RecordOrderDelivery(orderId, deliveredAt));

    externalEvents.shouldReceiveMessages(
      new OrderExternalEvent.OrderCompleted(
        orderId, clientId, productItems, totalPrice, deliveredAt
      )
    );
  }

  @Test
  public void cancellingForwardsOrderCancelledWithThePublicReason() {
    confirm();

    commandBus.send(new CancelOrder(orderId, OrderCancellationReason.Requested));

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
    commandBus.send(new RecordOrderPaymentAuthorization(orderId, paymentId, authorizedAt));
    externalEvents.reset();

    commandBus.send(new RecordOrderShipmentFailure(orderId, reservedAt));

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
    commandBus.send(new RecordOrderStockReservation(orderId, shipmentId, reservedAt));
    externalEvents.reset();

    commandBus.send(new RecordOrderPaymentFailure(orderId, authorizedAt));

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
  public void theForwarderReadsASummaryThatAlreadyHasTheEventItIsForwarding() {
    var database = new Database();
    var summaries = new OrderSummaries(database);
    var internalEventBus = new InMemoryEventBus();
    var statusWhenForwarded = new AtomicReference<Order.Status>();

    integrationEventBus.use(
      ignored -> statusWhenForwarded.set(summaries.get(orderId).orElseThrow().getStatus())
    );

    var forwarder = new OrderExternalEventForwarder(summaries, integrationEventBus);

    internalEventBus
      .subscribeWithMetadata(OrderEvent.OrderInitialized.class, summaries::onInitialized)
      .subscribeWithMetadata(OrderEvent.OrderStockReserved.class, summaries::onStockReserved)
      .subscribeWithMetadata(OrderEvent.OrderConfirmed.class, summaries::onConfirmed)
      .subscribe(OrderEvent.OrderConfirmed.class, forwarder::on);

    internalEventBus.publishAt(
      OrderFacade.mapToStreamId(orderId), 0,
      new OrderEvent.OrderInitialized(orderId, cartId, clientId, productItems, totalPrice, now),
      new OrderEvent.OrderStockReserved(orderId, shipmentId, reservedAt),
      new OrderEvent.OrderConfirmed(orderId, paymentId, shipmentId, reservedAt)
    );

    assertThat(statusWhenForwarded.get()).isEqualTo(Order.Status.Confirmed);
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
