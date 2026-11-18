package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AddProductItemToShoppingCartTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();
    private final UUID shoes = UUID.randomUUID();

    public AddProductItemToShoppingCartTests() {
        super("customers");
    }

    @Test
    public void addProductItem_succeeds_withFirstVersion_forCustomerWithoutOpenCart() {
        given(() -> Map.of("productId", shoes, "quantity", 2))
                .when(POST("/%s/shopping-carts/current/product-items".formatted(customerId)))
                .then(response -> {
                    OK.accept(response);
                    assertEquals("W/\"0\"", response.getResponseHeaders().getETag());
                });
    }

    @Test
    public void addProductItem_succeeds_withNextVersion_forOpenCart() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId).withProduct(shoes, 1));

        given(() -> Map.of("productId", shoes, "quantity", 2))
                .when(POST("/%s/shopping-carts/current/product-items".formatted(customerId)))
                .then(response -> {
                    OK.accept(response);
                    assertEquals("W/\"1\"", response.getResponseHeaders().getETag());
                });
    }

    @Test
    public void addProductItem_succeeds_byStartingANewCart_afterCheckout() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(shoes, 1)
                        .checkedOut());

        given(() -> Map.of("productId", shoes, "quantity", 2))
                .when(POST("/%s/shopping-carts/current/product-items".formatted(customerId)))
                .then(response -> {
                    OK.accept(response);
                    assertEquals("W/\"0\"", response.getResponseHeaders().getETag());
                });
    }

    @Test
    public void addProductItem_fails_withBadRequest_forZeroQuantity() {
        given(() -> Map.of("productId", shoes, "quantity", 0))
                .when(POST("/%s/shopping-carts/current/product-items".formatted(customerId)))
                .then(BAD_REQUEST);
    }

    @Test
    public void addProductItem_fails_withBadRequest_forMissingProductId() {
        given(() -> Map.of("quantity", 2))
                .when(POST("/%s/shopping-carts/current/product-items".formatted(customerId)))
                .then(BAD_REQUEST);
    }
}
