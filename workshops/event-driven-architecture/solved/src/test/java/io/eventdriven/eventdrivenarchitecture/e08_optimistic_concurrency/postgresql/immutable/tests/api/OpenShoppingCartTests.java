package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.tests.api;

import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.app.ECommerceApplication;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.postgresql.immutable.app.api.ShoppingCartsRequests;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.testing.ApiSpecification;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.testing.HttpEntityUtils.toHttpEntity;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    given(() -> toHttpEntity(new JSONObject()))
      .when(POST)
      .then(BAD_REQUEST);
  }
}
