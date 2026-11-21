package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
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
                    assertThat(response.getResponseHeaders().getETag()).isEqualTo("W/\"0\"");

                    var cart = (JsonNode) response.getResponseBody();
                    var line = cart.get("lines").get(0);
                    assertThat(line.get("productId").asString()).isEqualTo(shoes.toString());
                    assertThat(line.get("quantity").asInt()).isEqualTo(2);
                    assertThat(cart.get("totalPrice").asDouble())
                            .isCloseTo(line.get("unitPrice").asDouble() * 2, within(0.001));
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
