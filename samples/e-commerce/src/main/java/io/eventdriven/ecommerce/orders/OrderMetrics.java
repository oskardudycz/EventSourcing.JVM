package io.eventdriven.ecommerce.orders;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Reactor;
import io.eventdriven.ecommerce.orders.OrderEvent.OrderCompleted;
import io.eventdriven.ecommerce.orders.OrderEvent.OrderConfirmed;
import io.eventdriven.ecommerce.orders.OrderEvent.OrderPackageSent;
import io.eventdriven.ecommerce.orders.OrderEvent.OrderPaymentCaptured;
import io.eventdriven.ecommerce.orders.OrderEvent.OrderPlaced;
import io.eventdriven.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The business metrics of the orders: the revenue, how long an order takes to be paid, sent and completed, and how
 * many orders are still running or wait for the customer to pay again.
 */
public class OrderMetrics implements Reactor {
    private static final String STAGE = "ecommerce.order.stage";

    private final EventStore eventStore;
    private final DistributionSummary revenue;
    private final Timer paid;
    private final Timer sent;
    private final Timer completed;

    public OrderMetrics(
            EventStore eventStore,
            MeterRegistry meters,
            PendingOrders pendingOrders,
            OrdersAwaitingPayment ordersAwaitingPayment) {
        this.eventStore = eventStore;
        this.revenue = DistributionSummary.builder("ecommerce.order.revenue")
                .description("The amounts charged for completed orders")
                .baseUnit("dollars")
                .register(meters);
        this.paid = leadTime(meters, "paid");
        this.sent = leadTime(meters, "sent");
        this.completed = leadTime(meters, "completed");

        Gauge.builder("ecommerce.orders.in.progress", pendingOrders, PendingOrders::count)
                .description("Orders placed and not yet completed, canceled or closed unpaid")
                .register(meters);
        Gauge.builder("ecommerce.orders.awaiting.payment", ordersAwaitingPayment, OrdersAwaitingPayment::count)
                .description("Orders whose payment failed and that wait for the customer to pay again")
                .register(meters);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(OrderConfirmed.class, OrderPackageSent.class, OrderPaymentCaptured.class, OrderCompleted.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        switch (envelope.message()) {
            case OrderConfirmed event -> paid.record(sincePlaced(envelope, event.confirmedAt()));
            case OrderPackageSent event -> sent.record(sincePlaced(envelope, event.sentAt()));
            case OrderPaymentCaptured event -> revenue.record(event.amount());
            case OrderCompleted event -> completed.record(sincePlaced(envelope, event.completedAt()));
            default -> throw new IllegalArgumentException("Unexpected message %s".formatted(envelope.message()));
        }
    }

    private Duration sincePlaced(MessageEnvelope<Object> envelope, OffsetDateTime at) {
        if (!(eventStore.read(envelope.metadata().stream().streamId())
                instanceof EventStore.ReadResult.Success(var events))) return Duration.ZERO;

        return events.stream()
                .map(MessageEnvelope::message)
                .filter(OrderPlaced.class::isInstance)
                .map(placed -> Duration.between(((OrderPlaced) placed).placedAt(), at))
                .findFirst()
                .orElse(Duration.ZERO);
    }

    private static Timer leadTime(MeterRegistry meters, String stage) {
        return Timer.builder("ecommerce.order.lead.time")
                .description("Time from placing an order to the stage")
                .tag(STAGE, stage)
                .publishPercentileHistogram()
                .register(meters);
    }
}
