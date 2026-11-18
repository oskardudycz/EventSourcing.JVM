package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GetCartTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();
    private final UUID shoes = UUID.randomUUID();

    public GetCartTests() {
        super("customers");
    }

    @Test
    public void getCart_succeeds_withLinesTotalAndVersion() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId).withProduct(shoes, 2));

        given(() -> "")
                .when(GET("/%s/shopping-carts/current".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    assertEquals("W/\"0\"", response.getResponseHeaders().getETag());

                    var cart = (JsonNode) response.getResponseBody();
                    var line = cart.get("lines").get(0);
                    assertEquals(shoes.toString(), line.get("productId").asString());
                    assertEquals(2, line.get("quantity").asInt());
                    assertEquals(
                            line.get("unitPrice").asDouble() * 2,
                            cart.get("totalPrice").asDouble(),
                            0.001);
                });
    }

    @Test
    public void getCart_fails_withNotFound_forCustomerWithoutOpenCart() {
        given(() -> "")
                .when(GET("/%s/shopping-carts/current".formatted(customerId), JsonNode.class))
                .then(NOT_FOUND);
    }

    @Test
    public void getCart_fails_withNotFound_afterCheckout() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(shoes, 2)
                        .checkedOut());

        given(() -> "")
                .when(GET("/%s/shopping-carts/current".formatted(customerId), JsonNode.class))
                .then(NOT_FOUND);
    }
}
