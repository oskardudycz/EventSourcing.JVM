package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.Metadata;
import io.eventdriven.testing.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class PendingOrdersTests {
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();
    private final PricedProductItem[] productItems =
            new PricedProductItem[] {new PricedProductItem(UUID.randomUUID(), 2, 12.5)};
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
                        OrderPaymentStatus.NotAuthorized,
                        null,
                        OrderShipmentStatus.NotReserved,
                        OrderCancellationReason.Requested,
                        later)));

        assertThat(pendingOrderIds()).doesNotContain(orderId);
    }

    private List<OrderId> pendingOrderIds() {
        return pendingOrders.all().stream().map(PendingOrder::getOrderId).toList();
    }

    private void place(OrderId orderId, OffsetDateTime placedAt) {
        pendingOrders.handle(
                at(orderId, 0, new OrderPlaced(orderId, cartId, customerId, productItems, totalPrice, placedAt)));
    }

    private <Event> EventEnvelope<Event> at(OrderId orderId, long position, Event event) {
        return new EventEnvelope<>(event, Metadata.at(OrderFacade.mapToStreamId(orderId), position));
    }
}
