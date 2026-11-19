package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PurchaseOverHttpTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();

    public PurchaseOverHttpTests() {
        super("customers");
    }

    @Test
    public void aCustomerChecksOutPaysOnTheGatewaysPageAndTheSandboxWarehouseShipsTheOrder() {
        var builder = PurchaseRestBuilder.of(restTestClient, port);
        var order = builder.build(purchase -> purchase.withCustomerId(customerId)
                .withProduct(UUID.randomUUID(), 2)
                .checkedOut());

        var checkoutPage = builder.openCheckout(order.orderId(), order.paymentId());
        var backToTheShop = builder.payOn(checkoutPage, "4242 4242 4242 4242");

        assertThat(backToTheShop.endsWith("/shop/customers/%s/orders/%s".formatted(customerId, order.orderId())))
                .isTrue();

        given(() -> order.orderId())
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    var status = (JsonNode) response.getResponseBody();
                    assertThat(status.get("progress").asString()).isEqualTo("Completed");
                    assertThat(status.get("carrier").asString()).isEqualTo("Sandbox Express");
                });

        given(() -> "")
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                .get(0)
                                .get("progress")
                                .asString())
                        .isEqualTo("Completed"));
    }
}
