package io.eventdriven.ecommerce.orders.getorderstatus;

import static io.eventdriven.ecommerce.orders.getorderstatus.OrderAction.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderViewsFixture;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class OrderStatusesTests {
    private final OrderStatuses orderStatuses;
    private final OrderViewsFixture order;

    OrderStatusesTests(ProjectionsDatabase database) {
        orderStatuses = new OrderStatuses(database.bean(OrderStatusRepository.class));
        order = new OrderViewsFixture(orderStatuses);
    }

    @Test
    public void aPlacedOrderCanBePaidNowWithItsFirstPaymentOrCanceled() {
        order.placed();

        var status = status();

        assertThat(status.getCustomerId()).isEqualTo(order.customerId);
        assertThat(status.getProductItems()).containsExactly(order.productItems);
        assertThat(status.getTotalPrice()).isEqualTo(100);
        assertThat(status.getProgress()).isEqualTo(Order.Progress.AwaitingPayment);
        assertThat(status.getPaymentId()).isEqualTo(order.paymentId);
        assertThat(status.getActions()).containsExactly(PayNow, CancelOrder);
    }

    @Test
    public void anUnpaidOrderBeforeConfirmationCanBePaidAgainOrCanceled() {
        order.placed().stockReserved().paymentFailed();

        assertThat(status().getProgress()).isEqualTo(Order.Progress.PaymentFailed);
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

        assertThat(status().getProgress()).isEqualTo(Order.Progress.Processing);
        assertThat(status().getActions()).isEmpty();
    }

    @Test
    public void aShippedOrderShowsTheCarrierAndTheTrackingNumber() {
        order.placed().authorized(order.paymentId).stockReserved().confirmed().sent();

        assertThat(status().getProgress()).isEqualTo(Order.Progress.Shipped);
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

        assertThat(status().getProgress()).isEqualTo(Order.Progress.Completed);
        assertThat(status().getActions()).isEmpty();
    }

    @Test
    public void aCanceledOrderOffersNothing() {
        order.placed().canceled();

        assertThat(status().getProgress()).isEqualTo(Order.Progress.Canceled);
        assertThat(status().getActions()).isEmpty();
    }

    private OrderStatus status() {
        return orderStatuses.get(order.orderId).orElseThrow();
    }
}
