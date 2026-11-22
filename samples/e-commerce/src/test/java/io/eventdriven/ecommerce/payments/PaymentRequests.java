package io.eventdriven.ecommerce.payments;

import io.eventdriven.testing.api.ApiE2ESpecification.TestRequest;

public final class PaymentRequests {
    private PaymentRequests() {}

    public static TestRequest sandboxCheckoutPage(String sessionId) {
        return request -> request.get().uri("/sandbox/checkout/{sessionId}", sessionId);
    }
}
