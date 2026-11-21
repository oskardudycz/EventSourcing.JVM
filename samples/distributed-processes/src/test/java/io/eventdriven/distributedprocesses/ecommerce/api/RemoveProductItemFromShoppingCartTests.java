package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ETag;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RemoveProductItemFromShoppingCartTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();
    private final UUID shoes = UUID.randomUUID();
    private ETag eTag;

    public RemoveProductItemFromShoppingCartTests() {
        super("customers");
    }

    @BeforeEach
    public void addShoesToCart() {
        eTag = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId).withProduct(shoes, 2))
                .cartETag();
    }

    @Test
    public void removeProductItem_succeeds_withNextVersion_forSomeOfTheQuantity() {
        given(() -> "productId=%s&quantity=1".formatted(shoes))
                .when(DELETE(cartItems(customerId), eTag))
                .then(response -> {
                    OK.accept(response);
                    assertThat(response.getResponseHeaders().getETag()).isEqualTo("W/\"1\"");
                });
    }

    @Test
    public void removeProductItem_succeeds_forAllOfTheQuantity() {
        given(() -> "productId=%s&quantity=2".formatted(shoes))
                .when(DELETE(cartItems(customerId), eTag))
                .then(OK);
    }

    @Test
    public void removeProductItem_fails_withNotFound_forCustomerWithoutOpenCart() {
        given(() -> "productId=%s&quantity=1".formatted(shoes))
                .when(DELETE(cartItems(UUID.randomUUID()), eTag))
                .then(NOT_FOUND);
    }

    @Test
    public void removeProductItem_fails_withConflict_forProductNotInCart() {
        given(() -> "productId=%s&quantity=1".formatted(UUID.randomUUID()))
                .when(DELETE(cartItems(customerId), eTag))
                .then(CONFLICT);
    }

    @Test
    public void removeProductItem_fails_withConflict_forMoreThanInCart() {
        given(() -> "productId=%s&quantity=3".formatted(shoes))
                .when(DELETE(cartItems(customerId), eTag))
                .then(CONFLICT);
    }

    @Test
    public void removeProductItem_fails_withPreconditionFailed_forWrongETag() {
        given(() -> "productId=%s&quantity=1".formatted(shoes))
                .when(DELETE(cartItems(customerId), new ETag("999", true)))
                .then(PRECONDITION_FAILED);
    }

    @Test
    public void removeProductItem_fails_withBadRequest_forMissingIfMatch() {
        given(() -> "productId=%s&quantity=1".formatted(shoes))
                .when(DELETE(cartItems(customerId), null))
                .then(BAD_REQUEST);
    }

    private String cartItems(UUID customerId) {
        return "/%s/shopping-carts/current/product-items".formatted(customerId);
    }
}
