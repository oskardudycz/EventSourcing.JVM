package io.eventdriven.ecommerce.orders.getcustomerorders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderViewsFixture;
import io.eventdriven.testing.projections.ProjectionsDatabase;
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
            assertThat(listed.getProgress()).isEqualTo(Order.Progress.AwaitingPayment);
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
        assertThat(progress()).isEqualTo(Order.Progress.Processing);

        order.sent();
        assertThat(progress()).isEqualTo(Order.Progress.Shipped);

        order.captured().completed();
        assertThat(progress()).isEqualTo(Order.Progress.Completed);
    }

    @Test
    public void aFailedPaymentShowsUntilTheRetriedPaymentIsAuthorized() {
        order.placed().stockReserved().paymentFailed();
        assertThat(progress()).isEqualTo(Order.Progress.PaymentFailed);

        order.retried();
        assertThat(progress()).isEqualTo(Order.Progress.AwaitingPayment);

        order.authorized(order.retriedPaymentId).confirmed();
        assertThat(progress()).isEqualTo(Order.Progress.Processing);
    }

    @Test
    public void aPaymentRetriedAfterShippingShowsTheOrderShippedOnceAuthorized() {
        order.placed()
                .authorized(order.paymentId)
                .stockReserved()
                .confirmed()
                .sent()
                .paymentFailed();
        assertThat(progress()).isEqualTo(Order.Progress.PaymentFailed);

        order.retried().authorized(order.retriedPaymentId);
        assertThat(progress()).isEqualTo(Order.Progress.Shipped);
    }

    @Test
    public void aCanceledOrderShowsCanceled() {
        order.placed().canceled();

        assertThat(progress()).isEqualTo(Order.Progress.Canceled);
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

        assertThat(progress()).isEqualTo(Order.Progress.ClosedUnpaid);
    }

    private Order.Progress progress() {
        return customerOrders.of(order.customerId).getFirst().getProgress();
    }
}
