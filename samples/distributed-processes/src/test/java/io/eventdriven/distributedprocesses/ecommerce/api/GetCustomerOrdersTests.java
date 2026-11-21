package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GetCustomerOrdersTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();

    public GetCustomerOrdersTests() {
        super("customers");
    }

    @Test
    public void getCustomerOrders_succeeds_withTheNewestOrderFirst() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .paid());
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 1)
                        .checkedOut());

        given(() -> "")
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    var orders = (JsonNode) response.getResponseBody();
                    assertThat(orders.size()).isEqualTo(2);
                    assertThat(orders.get(0).get("orderId").asString())
                            .isEqualTo("urn:ecommerce:order:%s-2".formatted(customerId));
                    assertThat(orders.get(0).get("progress").asString()).isEqualTo("AwaitingPayment");
                    assertThat(orders.get(1).get("orderId").asString())
                            .isEqualTo("urn:ecommerce:order:%s-1".formatted(customerId));
                    assertThat(orders.get(1).get("progress").asString()).isEqualTo("Completed");
                });
    }

    @Test
    public void getCustomerOrders_succeeds_withNoOrders_forCustomerWhoNeverCheckedOut() {
        given(() -> "")
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    assertThat(((JsonNode) response.getResponseBody()).size()).isEqualTo(0);
                });
    }
}
