package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.ECommerceApplication;
import io.eventdriven.ecommerce.api.controller.builders.ShoppingCartRestBuilder;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests.AddProduct;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.testing.ApiSpecification;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;

import java.util.UUID;
import java.util.stream.Stream;

import static io.eventdriven.ecommerce.testing.HttpEntityUtils.toHttpEntity;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AddProductItemToShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
  private ETag eTag;

  public AddProductItemToShoppingCartTests() {
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
  public void addProductItem_succeeds_forValidDataAndExistingShoppingCart() {
    given(() ->
      new AddProduct(new ProductItemRequest(
        UUID.randomUUID(),
        2
      )))
      .when(POST("%s/products".formatted(shoppingCartId), eTag))
      .then(OK);
  }

  @Test
  public void addProductItem_succeeds_forValidDataAndNonEmptyExistingShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart
          .withClientId(clientId)
          .withProduct(new ProductItemRequest(UUID.randomUUID(), 10))
        );

    given(() ->
      new AddProduct(new ProductItemRequest(
        UUID.randomUUID(),
        2
      )))
      .when(POST("%s/products".formatted(result.id()), result.eTag()))
      .then(OK);
  }

  @ParameterizedTest
  @MethodSource("invalidBodiesProvider")
  public void addProductItem_fails_withBadRequest_forInvalidBody(HttpEntity<String> invalidBody) {
    given(() -> invalidBody)
      .when(POST)
      .then(BAD_REQUEST);
  }

  @Test
  public void addProductItem_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() ->
      new AddProduct(new ProductItemRequest(
        UUID.randomUUID(),
        2
      )))
      .when(POST("%s/products".formatted(notExistingId), eTag))
      .then(NOT_FOUND);
  }

  @Test
  public void addProductItem_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() ->
      new AddProduct(new ProductItemRequest(
        UUID.randomUUID(),
        2
      )))
      .when(POST("%s/products".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void addProductItem_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() ->
      new AddProduct(new ProductItemRequest(
        UUID.randomUUID(),
        2
      )))
      .when(POST("%s/products".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void addProductItem_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> new AddProduct(new ProductItemRequest(UUID.randomUUID(), 2)))
      .when(POST("%s/products".formatted(shoppingCartId), wrongETag))
      .then(PRECONDITION_FAILED);
  }

  static Stream<HttpEntity<String>> invalidBodiesProvider() {
    try {
      return Stream.of(
        // empty Body
        toHttpEntity(new JSONObject()),
        // missing quantity
        toHttpEntity(new JSONObject("{ \"productId\": \"%s\" }".formatted(UUID.randomUUID()))),
        // missing productId
        toHttpEntity(new JSONObject("{ \"quantity\": %s }".formatted(UUID.randomUUID()))),
        // zero quantity
        toHttpEntity(new JSONObject("{ \"productId\": \"%s\", \"quantity\": %s }".formatted(UUID.randomUUID(), 0))),
        // negative quantity
        toHttpEntity(new JSONObject("{ \"productId\": \"%s\", \"quantity\": %s }".formatted(UUID.randomUUID(), -1)))
      );
    } catch (JSONException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
