package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import static io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderAction.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.projections.Database;
import org.junit.jupiter.api.Test;

public class OrderStatusesTests {
    private final OrderStatuses orderStatuses = new OrderStatuses(new Database());
    private final OrderViewsFixture order = new OrderViewsFixture(orderStatuses);

    @Test
    public void aPlacedOrderCanBePaidNowWithItsFirstPaymentOrCanceled() {
        order.placed();

        var status = status();

        assertThat(status.getCustomerId()).isEqualTo(order.customerId);
        assertThat(status.getProductItems()).containsExactly(order.productItems);
        assertThat(status.getTotalPrice()).isEqualTo(100);
        assertThat(status.getProgress()).isEqualTo(OrderProgress.AwaitingPayment);
        assertThat(status.getPaymentId()).isEqualTo(order.paymentId);
        assertThat(status.getActions()).containsExactly(PayNow, CancelOrder);
    }

    @Test
    public void anUnpaidOrderBeforeConfirmationCanBePaidAgainOrCanceled() {
        order.placed().stockReserved().paymentFailed();

        assertThat(status().getProgress()).isEqualTo(OrderProgress.PaymentFailed);
        assertThat(status().getActions()).containsExactly(RetryPayment, CancelOrder);
    }

    @Test
    public void aRetriedPaymentCanBePaidNowUnderItsOwnId() {
        order.placed().stockReserved().paymentFailed().retried();

        assertThat(status().getPaymentId()).isEqualTo(order.retriedPaymentId);
        assertThat(status().getActions()).containsExactly(PayNow, CancelOrder);
    }

    @Test
    public void aConfirmedOrderOffersNothing() {
        order.placed().authorized(order.paymentId).stockReserved().confirmed();

        assertThat(status().getProgress()).isEqualTo(OrderProgress.Processing);
        assertThat(status().getActions()).isEmpty();
    }

    @Test
    public void aShippedOrderShowsTheCarrierAndTheTrackingNumber() {
        order.placed().authorized(order.paymentId).stockReserved().confirmed().sent();

        assertThat(status().getProgress()).isEqualTo(OrderProgress.Shipped);
        assertThat(status().getCarrier()).isEqualTo("UPS");
        assertThat(status().getTrackingNumber()).isEqualTo("1Z999AA10123456784");
        assertThat(status().getActions()).isEmpty();
    }

    @Test
    public void aShippedOrderWhosePaymentFailedCanBePaidAgainButNotCanceled() {
        order.placed()
                .authorized(order.paymentId)
                .stockReserved()
                .confirmed()
                .sent()
                .paymentFailed();

        assertThat(status().getActions()).containsExactly(RetryPayment);
    }

    @Test
    public void anOrderWhoseStockFailedCanStillBeCanceledButNotPaid() {
        order.placed().shipmentFailed();

        assertThat(status().getActions()).containsExactly(CancelOrder);
    }

    @Test
    public void aCompletedOrderOffersNothing() {
        order.placed()
                .authorized(order.paymentId)
                .stockReserved()
                .confirmed()
                .sent()
                .captured()
                .completed();

        assertThat(status().getProgress()).isEqualTo(OrderProgress.Completed);
        assertThat(status().getActions()).isEmpty();
    }

    @Test
    public void aCanceledOrderOffersNothing() {
        order.placed().canceled();

        assertThat(status().getProgress()).isEqualTo(OrderProgress.Canceled);
        assertThat(status().getActions()).isEmpty();
    }

    private OrderStatus status() {
        return orderStatuses.get(order.orderId).orElseThrow();
    }
}
