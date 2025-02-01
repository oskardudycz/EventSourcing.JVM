package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mixed.tests.api;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mixed.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mixed.app.ECommerceApplication;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mixed.tests.api.builders.ShoppingCartRestBuilder;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CancelShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;

  public CancelShoppingCartTests() {
    super("api/shopping-carts");
  }

  @BeforeEach
  public void openShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId));

    shoppingCartId = result.id();
  }

  @Test
  public void cancel_succeeds_forValidDataAndExistingShoppingCart() {
    given(() -> shoppingCartId)
      .when(DELETE())
      .then(OK);
  }

  @Test
  public void cancel_succeeds_forValidDataAndNonEmptyExistingShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart
          .withClientId(clientId)
          .withProduct(new ProductItemRequest(UUID.randomUUID(), 10))
        );

    given(() -> result.id())
      .when(DELETE())
      .then(OK);
  }

  @Test
  public void cancel_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(DELETE())
      .then(METHOD_NOT_ALLOWED);
  }

  @Test
  public void cancel_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> notExistingId)
      .when(DELETE())
      .then(NOT_FOUND);
  }

  @Test
  public void cancel_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> result.id())
      .when(DELETE())
      .then(CONFLICT);
  }

  @Test
  public void cancel_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> result.id())
      .when(DELETE())
      .then(CONFLICT);
  }
}
