package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfigurations.UnreachableGateway.class)
public class CreateCheckoutSessionWithUnreachableGatewayTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();

    public CreateCheckoutSessionWithUnreachableGatewayTests() {
        super("customers");
    }

    @Test
    public void createCheckoutSession_fails_withBadGateway_andTheCustomerCanStillPayNow() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .checkedOut());

        given(() -> null)
                .when(POST("/%s/orders/%s/payments/%s/checkout-session"
                        .formatted(customerId, order.orderId(), order.paymentId())))
                .then(BAD_GATEWAY);

        var status = GET("/%s/orders".formatted(customerId), JsonNode.class)
                .apply(restTestClient, order.orderId())
                .getResponseBody();
        assertThat(status.get("actions").get(0).asString()).isEqualTo("PayNow");
    }
}
