package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.ECommerceApplication;
import io.eventdriven.ecommerce.api.controller.builders.ShoppingCartRestBuilder;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.ecommerce.core.http.ETag;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetails;
import io.eventdriven.ecommerce.shoppingcarts.gettingbyid.ShoppingCartDetailsProductItem;
import io.eventdriven.ecommerce.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.stream.Stream;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RemoveProductItemFromShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
  private ShoppingCartDetailsProductItem product;
  private ETag eTag;

  public RemoveProductItemFromShoppingCartTests() {
    super("api/shopping-carts");
  }

  @BeforeEach
  public void openShoppingCartWithProduct() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart
          .withClientId(clientId)
          .withProduct(new ProductItemRequest(UUID.randomUUID(), 10))
        );

    shoppingCartId = result.id();
    eTag = result.eTag();

    var getResult = GET(ETag.weak(eTag.toLong() - 1), ShoppingCartDetails.class)
      .apply(restTemplate, result.id().toString());

    product = getResult.getBody().getProductItems().get(0);
  }

  @Test
  public void removeProductItem_succeeds_forNotAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity() - 1))
      .when(DELETE("%s/products/".formatted(shoppingCartId), eTag))
      .then(OK);
  }

  @Test
  public void removeProductItem_succeeds_forAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("%s/products/".formatted(shoppingCartId), eTag))
      .then(OK);
  }

  @ParameterizedTest
  @MethodSource("invalidURLsProvider")
  public void removeProductItem_fails_withBadRequest_forInvalidURL(String invalidURL) {
    given(() -> invalidURL)
      .when(DELETE("%s/products/".formatted(shoppingCartId), eTag))
      .then(BAD_REQUEST);
  }


  @Test
  public void removeProductItem_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(DELETE("%s/products/".formatted(shoppingCartId), eTag))
      .then(METHOD_NOT_ALLOWED);
  }

  @Test
  public void removeProductItem_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("%s/products/".formatted(notExistingId), eTag))
      .then(NOT_FOUND);
  }

  @Test
  public void removeProductItem_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("%s/products/".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void removeProductItem_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("%s/products/".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void removeProductItem_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("%s/products/".formatted(shoppingCartId), wrongETag))
      .then(PRECONDITION_FAILED);
  }

  static Stream<String> invalidURLsProvider() {
    return Stream.of(
      // missing quantity
      "%s?price=%s".formatted(UUID.randomUUID(), 1),
      // missing price
      "%s?quantity=%s".formatted(UUID.randomUUID(), 1),
      // zero quantity
      "%s?price=%s&quantity=%s".formatted(UUID.randomUUID(), 0, -1),
      // negative quantity
      "%s?price=%s&quantity=%s".formatted(UUID.randomUUID(), 1, -1),
      // zero price
      "%s?price=%s&quantity=%s".formatted(UUID.randomUUID(), 0, 1),
      // negative price
      "%s?price=%s&quantity=%s".formatted(UUID.randomUUID(), -1, 1)
    );
  }
}
