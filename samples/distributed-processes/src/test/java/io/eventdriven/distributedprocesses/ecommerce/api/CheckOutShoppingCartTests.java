package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ETag;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
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
            assertThat(response.getResponseHeaders().getLocation().toString())
                    .isEqualTo("/customers/%s/orders/%s-1".formatted(customerId, customerId));
        });
    }

    @Test
    public void checkOut_fails_withConflictAndTheNewVersion_forChangedPrice() {
        prices.set(shoes, 60);

        given(() -> null).when(POST(checkout(customerId), eTag)).then(response -> {
            CONFLICT.accept(response);
            assertThat(response.getResponseHeaders().getETag()).isEqualTo("W/\"1\"");
        });

        var cart = GET("/%s/shopping-carts/current".formatted(customerId), JsonNode.class)
                .apply(restTestClient, "");
        assertThat(cart.getResponseBody().get("lines").get(0).get("unitPrice").asDouble())
                .isEqualTo(60);
        assertThat(cart.getResponseBody().get("checkedOut").asBoolean()).isFalse();
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
        given(() -> null)
                .when(POST(checkout(customerId), new ETag("999", true)))
                .then(PRECONDITION_FAILED);
    }

    @Test
    public void checkOut_fails_withPreconditionFailed_forANegativeVersionInIfMatch() {
        given(() -> null).when(POST(checkout(customerId), new ETag("-1", true))).then(PRECONDITION_FAILED);
    }

    @Test
    public void checkOut_fails_withBadRequest_forMissingIfMatch() {
        given(() -> null).when(POST(checkout(customerId))).then(BAD_REQUEST);
    }

    @Test
    public void checkOut_fails_withBadRequest_forAMalformedIfMatch() {
        given(() -> null)
                .when(POST(checkout(customerId), new ETag("not-a-version", false)))
                .then(BAD_REQUEST);
    }

    @Test
    public void checkOut_fails_withBadRequest_forAnEmptyIfMatch() {
        given(() -> new HttpEntity<>(null, emptyIfMatch()))
                .when(POST(checkout(customerId)))
                .then(BAD_REQUEST);
    }

    private static HttpHeaders emptyIfMatch() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.IF_MATCH, "");

        return headers;
    }

    private String checkout(UUID customerId) {
        return "/%s/shopping-carts/current/checkout".formatted(customerId);
    }
}
