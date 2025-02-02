package io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.tests.api;

import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.core.http.ETag;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.ECommerceApplication;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.ShoppingCart;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.app.shoppingcarts.productItems.ProductItems;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.mongodb.mutable.tests.api.builders.ShoppingCartRestBuilder;
import io.eventdriven.eventdrivenarchitecture.e08_optimistic_concurrency.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RemoveProductItemFromShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
  private ETag eTag;
  private ProductItems.PricedProductItem product;

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

    var getResult = GET(ETag.weak(eTag.toLong() - 1), ShoppingCart.class)
      .apply(restTemplate, result.id().toString());

    product = getResult.getBody().productItems()[0];
  }


  @Test
  public void removeProductItem_succeeds_forNotAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity() - 1))
      .when(DELETE("/%s/products".formatted(shoppingCartId), eTag))
      .then(OK);
  }


  @Test
  public void removeProductItem_succeeds_forAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(shoppingCartId), eTag))
      .then(OK);
  }


  @Test
  public void removeProductItem_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(DELETE("/%s/products".formatted(shoppingCartId), eTag))
      .then(METHOD_NOT_ALLOWED);
  }


  @Test
  public void removeProductItem_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(notExistingId), eTag))
      .then(NOT_FOUND);
  }


  @Test
  public void removeProductItem_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }


  @Test
  public void removeProductItem_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(result.id()), result.eTag()))
      .then(CONFLICT);
  }


  @Test
  public void removeProductItem_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(shoppingCartId), wrongETag))
      .then(PRECONDITION_FAILED);
  }
}
