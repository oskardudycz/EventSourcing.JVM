package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.testing.ApiSpecification;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfigurations.MovableClock.class)
public class RetryOrderPaymentTests extends ApiSpecification {
    @Autowired
    private TestConfigurations.Clock clock;

    @Autowired
    private ScheduledPaymentTimeoutWorker paymentTimeoutWorker;

    private final UUID customerId = UUID.randomUUID();

    public RetryOrderPaymentTests() {
        super("customers");
    }

    @Test
    public void retryOrderPayment_succeeds_withANewPaymentToPay_afterThePaymentTimedOut() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .checkedOut());
        clock.moveBy(Duration.ofHours(25));
        paymentTimeoutWorker.handleAll();
        assertThat(statusOf(order.orderId()).get("progress").asString()).isEqualTo("PaymentFailed");

        given(() -> null)
                .when(POST("/%s/orders/%s/retry-payment".formatted(customerId, order.orderId())))
                .then(ACCEPTED);

        var status = statusOf(order.orderId());
        assertThat(status.get("progress").asString()).isEqualTo("AwaitingPayment");
        assertThat(status.get("paymentId").asString())
                .isEqualTo("urn:ecommerce:payment:%s-2".formatted(order.orderId()));
        assertThat(status.get("actions").get(0).asString()).isEqualTo("PayNow");
    }

    @Test
    public void retryOrderPayment_succeeds_withAcceptedButChangesNothing_forPendingPayment() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .checkedOut());

        given(() -> null)
                .when(POST("/%s/orders/%s/retry-payment".formatted(customerId, order.orderId())))
                .then(ACCEPTED);

        assertThat(statusOf(order.orderId()).get("paymentId").asString())
                .isEqualTo("urn:ecommerce:payment:" + order.paymentId());
    }

    @Test
    public void retryOrderPayment_fails_withNotFound_forUnknownOrder() {
        given(() -> null)
                .when(POST("/%s/orders/%s-1/retry-payment".formatted(customerId, UUID.randomUUID())))
                .then(NOT_FOUND);
    }

    private JsonNode statusOf(String orderId) {
        return GET("/%s/orders".formatted(customerId), JsonNode.class)
                .apply(restTestClient, orderId)
                .getResponseBody();
    }
}
