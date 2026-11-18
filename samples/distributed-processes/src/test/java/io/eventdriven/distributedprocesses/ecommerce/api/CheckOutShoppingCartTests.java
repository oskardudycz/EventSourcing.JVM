package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfigurations.ChangeablePrices.class)
public class CheckOutShoppingCartTests extends ApiSpecification {
    @Autowired
    private TestConfigurations.Prices prices;

    private final UUID customerId = UUID.randomUUID();
    private final UUID shoes = UUID.randomUUID();
    private ETag eTag;

    public CheckOutShoppingCartTests() {
        super("customers");
    }

    @BeforeEach
    public void addShoesToCart() {
        prices.set(shoes, 50);
        eTag = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId).withProduct(shoes, 2))
                .cartETag();
    }

    @Test
    public void checkOut_succeeds_withAcceptedAndTheOrderStatusAsLocation() {
        given(() -> null).when(POST(checkout(customerId), eTag)).then(response -> {
            ACCEPTED.accept(response);
            assertEquals(
                    "/customers/%s/orders/%s-1".formatted(customerId, customerId),
                    response.getResponseHeaders().getLocation().toString());
        });
    }

    @Test
    public void checkOut_fails_withConflictAndTheNewVersion_forChangedPrice() {
        prices.set(shoes, 60);

        given(() -> null).when(POST(checkout(customerId), eTag)).then(response -> {
            CONFLICT.accept(response);
            assertEquals("W/\"1\"", response.getResponseHeaders().getETag());
        });

        var cart = GET("/%s/shopping-carts/current".formatted(customerId), JsonNode.class)
                .apply(restTestClient, "");
        assertEquals(
                60, cart.getResponseBody().get("lines").get(0).get("unitPrice").asDouble());
        assertFalse(cart.getResponseBody().get("checkedOut").asBoolean());
    }

    @Test
    public void checkOut_fails_withNotFound_forCustomerWithoutOpenCart() {
        given(() -> null).when(POST(checkout(UUID.randomUUID()), eTag)).then(NOT_FOUND);
    }

    @Test
    public void checkOut_fails_withNotFound_forCartAlreadyCheckedOut() {
        POST(checkout(customerId), eTag).apply(restTestClient, null);

        given(() -> null).when(POST(checkout(customerId), eTag)).then(NOT_FOUND);
    }

    @Test
    public void checkOut_fails_withPreconditionFailed_forWrongETag() {
        given(() -> null).when(POST(checkout(customerId), ETag.weak(999))).then(PRECONDITION_FAILED);
    }

    @Test
    public void checkOut_fails_withBadRequest_forMissingIfMatch() {
        given(() -> null).when(POST(checkout(customerId))).then(BAD_REQUEST);
    }

    private String checkout(UUID customerId) {
        return "/%s/shopping-carts/current/checkout".formatted(customerId);
    }
}
