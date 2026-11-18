package io.eventdriven.ecommerce.api.controller;

import static io.eventdriven.ecommerce.testing.HttpEntityUtils.toHttpEntity;

import io.eventdriven.ecommerce.ECommerceApplication;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.testing.ApiSpecification;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ECommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OpenShoppingCartTests extends ApiSpecification {
    public OpenShoppingCartTests() {
        super("api/shopping-carts");
    }

    @Test
    public void openShoppingCart_succeeds_forValidData() {
        given(() -> new ShoppingCartsRequests.Open(UUID.randomUUID()))
                .when(POST)
                .then(CREATED);
    }

    @Test
    public void openShoppingCart_fails_withBadRequest_forInvalidBody() {
        given(() -> toHttpEntity(new JSONObject())).when(POST).then(BAD_REQUEST);
    }
}
