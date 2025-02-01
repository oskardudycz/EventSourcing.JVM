package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.tests.api;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.ECommerceApplication;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mutable.app.api.ShoppingCartsRequests;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.testing.ApiSpecification;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.testing.HttpEntityUtils.toHttpEntity;

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
