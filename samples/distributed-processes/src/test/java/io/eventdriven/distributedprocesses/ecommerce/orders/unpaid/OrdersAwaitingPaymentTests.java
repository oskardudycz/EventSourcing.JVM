package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class OrdersAwaitingPaymentTests {
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final String streamId = OrderFacade.mapToStreamId(orderId);
    private final OffsetDateTime failedAt = OffsetDateTime.now();
    private final OffsetDateTime dueAt = failedAt.plusDays(14);

    private final Database database = new Database();
    private final OrdersAwaitingPayment ordersAwaitingPayment = new OrdersAwaitingPayment(database);

    @Test
    public void failedPaymentIsOverdueFromTheDateTheOrderGaveTheCustomer() {
        fail();

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).containsExactly(orderId);
        assertThat(ordersAwaitingPayment.overdueAt(dueAt.minusMinutes(1))).isEmpty();
    }

    @Test
    public void completedOrderStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(new OrderCompleted(orderId, failedAt), 1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).isEmpty();
    }

    @Test
    public void orderClosedUnpaidStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(new OrderClosedUnpaid(orderId, dueAt), 1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).isEmpty();
    }

    @Test
    public void canceledOrderStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(
                new OrderCanceled(
                        orderId,
                        null,
                        OrderPaymentStatus.NotAuthorized,
                        null,
                        OrderShipmentStatus.NotReserved,
                        OrderCancellationReason.PaymentFailed,
                        failedAt),
                1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).isEmpty();
    }

    @Test
    public void eventsDeliveredTwiceLeaveASingleOrderWaiting() {
        fail();
        fail();

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).containsExactly(orderId);

        var completed = at(new OrderCompleted(orderId, failedAt), 1);
        ordersAwaitingPayment.handle(completed);
        ordersAwaitingPayment.handle(completed);

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).isEmpty();
    }

    private void fail() {
        ordersAwaitingPayment.handle(at(new OrderPaymentFailed(orderId, failedAt, dueAt), 0));
    }

    private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
        return new EventEnvelope<>(event, new EventMetadata(streamId, streamPosition, streamPosition));
    }
}
