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
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PendingOrdersTests {
  private final OrderId orderId = OrderId.of(UUID.randomUUID());
  private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
  private final UUID clientId = UUID.randomUUID();
  private final PricedProductItem[] productItems = new PricedProductItem[]{
    new PricedProductItem(UUID.randomUUID(), 2, 12.5)
  };
  private final double totalPrice = 25;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime later = now.plusMinutes(10);

  private final Database database = new Database();
  private final PendingOrders pendingOrders = new PendingOrders(database);

  @Test
  public void placedOrderIsOnTheList() {
    place(orderId, now);

    assertThat(pendingOrders.all()).hasSize(1);

    var pending = pendingOrders.all().getFirst();

    assertThat(pending.getOrderId()).isEqualTo(orderId);
  }

  @Test
  public void completedOrderLeavesTheList() {
    place(orderId, now);

    pendingOrders.handle(at(orderId, 1, new OrderCompleted(orderId, later)));

    assertThat(pendingOrders.all()).isEmpty();
  }

  @Test
  public void orderClosedUnpaidLeavesTheList() {
    place(orderId, now);

    pendingOrders.handle(at(orderId, 1, new OrderClosedUnpaid(orderId, later)));

    assertThat(pendingOrders.all()).isEmpty();
  }

  @Test
  public void cancelledOrderLeavesTheList() {
    place(orderId, now);

    pendingOrders.handle(at(orderId, 1, new OrderCancelled(
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



  private void place(OrderId orderId, OffsetDateTime placedAt) {
    pendingOrders.handle(at(orderId, 0, new OrderPlaced(
      orderId, cartId, clientId, productItems, totalPrice, placedAt
    )));
  }

  private <Event> EventEnvelope<Event> at(OrderId orderId, long position, Event event) {
    return new EventEnvelope<>(event, new EventMetadata(OrderFacade.mapToStreamId(orderId), position, position));
  }
}
