package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.testing.ProjectionsDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class CustomerOrdersTests {
    private final CustomerOrders customerOrders;
    private final OrderViewsFixture order;

    CustomerOrdersTests(ProjectionsDatabase database) {
        customerOrders = new CustomerOrders(database.bean(CustomerOrderRepository.class));
        order = new OrderViewsFixture(customerOrders);
    }

    @Test
    public void aPlacedOrderIsListedForItsCustomerAsAwaitingPayment() {
        order.placed();

        assertThat(customerOrders.of(order.customerId)).singleElement().satisfies(listed -> {
            assertThat(listed.getOrderId()).isEqualTo(order.orderId);
            assertThat(listed.getPlacedAt()).isEqualTo(order.now);
            assertThat(listed.getTotalPrice()).isEqualTo(100);
            assertThat(listed.getProgress()).isEqualTo(OrderProgress.AwaitingPayment);
        });
    }

    @Test
    public void anotherCustomersOrdersAreNotListed() {
        order.placed();

        assertThat(customerOrders.of(UUID.randomUUID())).isEmpty();
    }

    @Test
    public void theOrderMovesThroughItsProgress() {
        order.placed().authorized(order.paymentId).stockReserved().confirmed();
        assertThat(progress()).isEqualTo(OrderProgress.Processing);

        order.sent();
        assertThat(progress()).isEqualTo(OrderProgress.Shipped);

        order.captured().completed();
        assertThat(progress()).isEqualTo(OrderProgress.Completed);
    }

    @Test
    public void aFailedPaymentShowsUntilTheRetriedPaymentIsAuthorized() {
        order.placed().stockReserved().paymentFailed();
        assertThat(progress()).isEqualTo(OrderProgress.PaymentFailed);

        order.retried();
        assertThat(progress()).isEqualTo(OrderProgress.AwaitingPayment);

        order.authorized(order.retriedPaymentId).confirmed();
        assertThat(progress()).isEqualTo(OrderProgress.Processing);
    }

    @Test
    public void aPaymentRetriedAfterShippingShowsTheOrderShippedOnceAuthorized() {
        order.placed()
                .authorized(order.paymentId)
                .stockReserved()
                .confirmed()
                .sent()
                .paymentFailed();
        assertThat(progress()).isEqualTo(OrderProgress.PaymentFailed);

        order.retried().authorized(order.retriedPaymentId);
        assertThat(progress()).isEqualTo(OrderProgress.Shipped);
    }

    @Test
    public void aCanceledOrderShowsCanceled() {
        order.placed().canceled();

        assertThat(progress()).isEqualTo(OrderProgress.Canceled);
    }

    @Test
    public void anOrderClosedUnpaidShowsClosedUnpaid() {
        order.placed()
                .authorized(order.paymentId)
                .stockReserved()
                .confirmed()
                .sent()
                .paymentFailed()
                .closedUnpaid();

        assertThat(progress()).isEqualTo(OrderProgress.ClosedUnpaid);
    }

    private OrderProgress progress() {
        return customerOrders.of(order.customerId).getFirst().getProgress();
    }
}
