package io.eventdriven.ecommerce.orders.pending;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderFacade;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class PendingOrdersTests {
    private final Order.Id orderId = Order.Id.of(UUID.randomUUID());
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();
    private final Order.PricedProductItem[] productItems =
            new Order.PricedProductItem[] {new Order.PricedProductItem(UUID.randomUUID(), 2, 12.5)};
    private final double totalPrice = 25;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime later = now.plusMinutes(10);

    private final PendingOrders pendingOrders;

    PendingOrdersTests(ProjectionsDatabase database) {
        pendingOrders = new PendingOrders(database.bean(PendingOrderRepository.class));
    }

    @Test
    public void placedOrderIsOnTheList() {
        place(orderId, now);

        assertThat(pendingOrderIds()).containsOnlyOnce(orderId);
    }

    @Test
    public void completedOrderLeavesTheList() {
        place(orderId, now);

        pendingOrders.handle(at(orderId, 1, new OrderCompleted(orderId, later)));

        assertThat(pendingOrderIds()).doesNotContain(orderId);
    }

    @Test
    public void orderClosedUnpaidLeavesTheList() {
        place(orderId, now);

        pendingOrders.handle(at(orderId, 1, new OrderClosedUnpaid(orderId, later)));

        assertThat(pendingOrderIds()).doesNotContain(orderId);
    }

    @Test
    public void canceledOrderLeavesTheList() {
        place(orderId, now);

        pendingOrders.handle(at(
                orderId,
                1,
                new OrderCanceled(
                        orderId,
                        null,
                        Order.PaymentStatus.NotAuthorized,
                        null,
                        Order.ShipmentStatus.NotReserved,
                        Order.CancellationReason.Requested,
                        later)));

        assertThat(pendingOrderIds()).doesNotContain(orderId);
    }

    private List<Order.Id> pendingOrderIds() {
        return pendingOrders.all().stream().map(PendingOrder::getOrderId).toList();
    }

    private void place(Order.Id orderId, OffsetDateTime placedAt) {
        pendingOrders.handle(
                at(orderId, 0, new OrderPlaced(orderId, cartId, customerId, productItems, totalPrice, placedAt)));
    }

    private <Event> EventEnvelope<Event> at(Order.Id orderId, long position, Event event) {
        return new EventEnvelope<>(event, Metadata.at(OrderFacade.mapToStreamId(orderId), position));
    }
}
