package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CancelOrderTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();

    public CancelOrderTests() {
        super("customers");
    }

    @Test
    public void cancelOrder_succeeds_withAcceptedAndCancelsTheUnpaidOrder() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .checkedOut());

        given(() -> null)
                .when(POST("/%s/orders/%s/cancel".formatted(customerId, order.orderId())))
                .then(ACCEPTED);

        assertThat(progressOf(order.orderId())).isEqualTo("Canceled");
    }

    @Test
    public void cancelOrder_succeeds_withAcceptedButChangesNothing_forCompletedOrder() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .paid());

        given(() -> null)
                .when(POST("/%s/orders/%s/cancel".formatted(customerId, order.orderId())))
                .then(ACCEPTED);

        assertThat(progressOf(order.orderId())).isEqualTo("Completed");
    }

    @Test
    public void cancelOrder_fails_withNotFound_forUnknownOrder() {
        given(() -> null)
                .when(POST("/%s/orders/%s-1/cancel".formatted(customerId, UUID.randomUUID())))
                .then(NOT_FOUND);
    }

    private String progressOf(String orderId) {
        return GET("/%s/orders".formatted(customerId), JsonNode.class)
                .apply(restTestClient, orderId)
                .getResponseBody()
                .get("progress")
                .asString();
    }
}
