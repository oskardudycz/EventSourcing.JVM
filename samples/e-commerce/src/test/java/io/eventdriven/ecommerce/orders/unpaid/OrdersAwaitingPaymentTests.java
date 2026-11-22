package io.eventdriven.ecommerce.orders.unpaid;

import static io.eventdriven.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderFacade;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class OrdersAwaitingPaymentTests {
    private final Order.Id orderId = Order.Id.of(UUID.randomUUID());
    private final String streamId = OrderFacade.mapToStreamId(orderId);
    private final OffsetDateTime failedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final OffsetDateTime dueAt = failedAt.plusDays(14);

    private final OrdersAwaitingPayment ordersAwaitingPayment;

    OrdersAwaitingPaymentTests(ProjectionsDatabase database) {
        ordersAwaitingPayment = new OrdersAwaitingPayment(database.bean(OrderAwaitingPaymentRepository.class));
    }

    @Test
    public void failedPaymentIsOverdueFromTheDateTheOrderGaveTheCustomer() {
        fail();

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).containsOnlyOnce(orderId);
        assertThat(ordersAwaitingPayment.overdueAt(dueAt.minusMinutes(1))).doesNotContain(orderId);
    }

    @Test
    public void completedOrderStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(new OrderCompleted(orderId, failedAt), 1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).doesNotContain(orderId);
    }

    @Test
    public void orderClosedUnpaidStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(new OrderClosedUnpaid(orderId, dueAt), 1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).doesNotContain(orderId);
    }

    @Test
    public void canceledOrderStopsWaiting() {
        fail();

        ordersAwaitingPayment.handle(at(
                new OrderCanceled(
                        orderId,
                        null,
                        Order.PaymentStatus.NotAuthorized,
                        null,
                        Order.ShipmentStatus.NotReserved,
                        Order.CancellationReason.PaymentFailed,
                        failedAt),
                1));

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).doesNotContain(orderId);
    }

    @Test
    public void eventsDeliveredTwiceLeaveASingleOrderWaiting() {
        fail();
        fail();

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).containsOnlyOnce(orderId);

        var completed = at(new OrderCompleted(orderId, failedAt), 1);
        ordersAwaitingPayment.handle(completed);
        ordersAwaitingPayment.handle(completed);

        assertThat(ordersAwaitingPayment.overdueAt(dueAt)).doesNotContain(orderId);
    }

    private void fail() {
        ordersAwaitingPayment.handle(at(new OrderPaymentFailed(orderId, failedAt, dueAt), 0));
    }

    private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
        return new EventEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
