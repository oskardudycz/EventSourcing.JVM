package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.ECommerceApplication;
import io.eventdriven.ecommerce.api.controller.builders.ShoppingCartRestBuilder;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfirmShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
  private ETag eTag;

  public ConfirmShoppingCartTests() {
    super("api/shopping-carts");
  }

  @BeforeEach
  public void openShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId));

    shoppingCartId = result.id();
    eTag = result.eTag();
  }

  @Test
  public void confirm_succeeds_forValidDataAndExistingShoppingCart() {
    given(() -> shoppingCartId)
      .when(PUT(eTag))
      .then(OK);
  }

  @Test
  public void confirm_succeeds_forValidDataAndNonEmptyExistingShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart
          .withClientId(clientId)
          .withProduct(new ProductItemRequest(UUID.randomUUID(), 10))
        );

    given(() -> result.id())
      .when(PUT(result.eTag()))
      .then(OK);
  }

  @Test
  public void confirm_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(PUT(eTag))
      .then(METHOD_NOT_ALLOWED);
  }

  @Test
  public void confirm_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> notExistingId)
      .when(PUT(eTag))
      .then(NOT_FOUND);
  }

  @Test
  public void confirm_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> result.id())
      .when(PUT(result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void confirm_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> result.id())
      .when(PUT(result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void confirm_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> shoppingCartId)
      .when(PUT(wrongETag))
      .then(PRECONDITION_FAILED);
  }
}
