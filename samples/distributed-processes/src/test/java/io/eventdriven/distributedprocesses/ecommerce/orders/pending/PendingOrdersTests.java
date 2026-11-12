package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PendingOrdersTests {
  private final OrderId orderId = OrderId.of(UUID.randomUUID());
  private final OrderId otherOrderId = OrderId.of(UUID.randomUUID());
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
  private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
  private final PricedProductItem[] productItems = new PricedProductItem[]{
    new PricedProductItem(UUID.randomUUID(), 2, 12.5)
  };
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime later = now.plusMinutes(10);
  private final OffsetDateTime threshold = now.plusMinutes(5);

  private final Database database = new Database();
  private final PendingOrders pendingOrders = new PendingOrders(database);

  @Test
  public void initializedOrderIsOnTheList() {
    initialize(orderId, now);

    assertThat(pendingOrders.all()).hasSize(1);

    var pending = pendingOrders.all().getFirst();

    assertThat(pending.getOrderId()).isEqualTo(orderId);
    assertThat(pending.getClientId()).isEqualTo(clientId);
    assertThat(pending.getTotalPrice()).isEqualTo(totalPrice);
    assertThat(pending.getInitializedAt()).isEqualTo(now);
    assertThat(pending.getPaymentStatus()).isEqualTo(OrderPaymentStatus.NotAuthorized);
    assertThat(pending.getShipmentStatus()).isEqualTo(OrderShipmentStatus.NotReserved);
  }

  @Test
  public void holdsTakenAlongTheWayAreKeptOnTheList() {
    initialize(orderId, now);

    pendingOrders.onPaymentAuthorized(at(orderId, 1, new OrderPaymentAuthorized(orderId, paymentId, now)));
    pendingOrders.onStockReserved(at(orderId, 2, new OrderStockReserved(orderId, shipmentId, now)));

    var pending = pendingOrders.all().getFirst();

    assertThat(pending.getPaymentStatus()).isEqualTo(OrderPaymentStatus.Authorized);
    assertThat(pending.getShipmentStatus()).isEqualTo(OrderShipmentStatus.Reserved);
  }

  @Test
  public void completedOrderLeavesTheList() {
    initialize(orderId, now);

    pendingOrders.onCompleted(at(orderId, 1, new OrderCompleted(orderId, later)));

    assertThat(pendingOrders.all()).isEmpty();
  }

  @Test
  public void cancelledOrderLeavesTheList() {
    initialize(orderId, now);

    pendingOrders.onCancelled(at(orderId, 1, new OrderCancelled(
      orderId,
      null,
      OrderPaymentStatus.NotAuthorized,
      null,
      OrderShipmentStatus.NotReserved,
      OrderCancellationReason.Requested,
      later
    )));

    assertThat(pendingOrders.all()).isEmpty();
  }

  @Test
  public void onlyOrdersInitializedBeforeTheThresholdAreOlderThanIt() {
    initialize(orderId, now);
    initialize(otherOrderId, later);

    assertThat(pendingOrders.olderThan(threshold)).containsExactly(orderId);
  }

  @Test
  public void anOrderThatLeftTheListIsNeverOlderThanTheThreshold() {
    initialize(orderId, now);
    pendingOrders.onCompleted(at(orderId, 1, new OrderCompleted(orderId, later)));

    assertThat(pendingOrders.olderThan(threshold)).isEmpty();
  }

  @Test
  public void redeliveredEventDoesNotUndoWhatCameAfterIt() {
    initialize(orderId, now);
    var authorization = at(orderId, 1, new OrderPaymentAuthorized(orderId, paymentId, now));
    pendingOrders.onPaymentAuthorized(authorization);
    pendingOrders.onPaymentCaptured(at(orderId, 2, new OrderPaymentCaptured(orderId, paymentId, totalPrice, now)));

    pendingOrders.onPaymentAuthorized(authorization);

    assertThat(pendingOrders.all().getFirst().getPaymentStatus()).isEqualTo(OrderPaymentStatus.Captured);
  }

  private void initialize(OrderId orderId, OffsetDateTime initializedAt) {
    pendingOrders.onInitialized(at(orderId, 0, new OrderInitialized(
      orderId, cartId, clientId, productItems, totalPrice, initializedAt
    )));
  }

  private <Event> EventEnvelope<Event> at(OrderId orderId, long position, Event event) {
    return new EventEnvelope<>(event, new EventMetadata(OrderFacade.mapToStreamId(orderId), position, position));
  }
}
