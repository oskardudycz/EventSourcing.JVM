package io.eventdriven.ecommerce.orders;

import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.testing.api.ApiE2ESpecification.TestRequest;
import java.util.UUID;

public final class OrderRequests {
    private OrderRequests() {}

    public static TestRequest getOrders(UUID customerId) {
        return request -> request.get().uri("/customers/{customerId}/orders", customerId);
    }

    public static TestRequest getOrderStatus(UUID customerId, Order.Id orderId) {
        return request -> request.get().uri("/customers/{customerId}/orders/{orderId}", customerId, orderId.tail());
    }

    public static TestRequest cancelOrder(UUID customerId, Order.Id orderId) {
        return request ->
                request.post().uri("/customers/{customerId}/orders/{orderId}/cancel", customerId, orderId.tail());
    }

    public static TestRequest retryOrderPayment(UUID customerId, Order.Id orderId) {
        return request -> request.post()
                .uri("/customers/{customerId}/orders/{orderId}/retry-payment", customerId, orderId.tail());
    }

    public static TestRequest payNow(UUID customerId, Order.Id orderId, Payment.Id paymentId) {
        return request -> request.post()
                .uri(
                        "/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session",
                        customerId,
                        orderId.tail(),
                        paymentId.tail());
    }

    public static TestRequest ordersPage(UUID customerId) {
        return request -> request.get().uri("/shop/customers/{customerId}/orders", customerId);
    }

    public static TestRequest orderPage(UUID customerId, Order.Id orderId) {
        return request ->
                request.get().uri("/shop/customers/{customerId}/orders/{orderId}", customerId, orderId.tail());
    }

    public static TestRequest cancelOrderOnPage(UUID customerId, Order.Id orderId) {
        return request ->
                request.post().uri("/shop/customers/{customerId}/orders/{orderId}/cancel", customerId, orderId.tail());
    }

    public static TestRequest retryOrderPaymentOnPage(UUID customerId, Order.Id orderId) {
        return request -> request.post()
                .uri("/shop/customers/{customerId}/orders/{orderId}/retry-payment", customerId, orderId.tail());
    }
}
