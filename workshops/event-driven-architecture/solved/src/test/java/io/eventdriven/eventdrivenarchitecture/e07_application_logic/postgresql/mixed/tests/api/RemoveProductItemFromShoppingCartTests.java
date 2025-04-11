package io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.tests.api;

import io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.app.ECommerceApplication;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.app.shoppingcarts.ShoppingCart;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.app.shoppingcarts.productItems.ProductItems;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.postgresql.mixed.tests.api.builders.ShoppingCartRestBuilder;
import io.eventdriven.eventdrivenarchitecture.e07_application_logic.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RemoveProductItemFromShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
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

    var getResult = GET(ShoppingCart.class)
      .apply(restTemplate, result.id().toString());

    product = getResult.getBody().productItems()[0];
  }

  @Test
  public void removeProductItem_succeeds_forNotAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity() - 1))
      .when(DELETE("/%s/products".formatted(shoppingCartId)))
      .then(OK);
  }

  @Test
  public void removeProductItem_succeeds_forAllProductsAndExistingShoppingCart() {
    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(shoppingCartId)))
      .then(OK);
  }

  @Test
  public void removeProductItem_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(DELETE("/%s/products".formatted(shoppingCartId)))
      .then(METHOD_NOT_ALLOWED);
  }

  @Test
  public void removeProductItem_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(notExistingId)))
      .then(NOT_FOUND);
  }

  @Test
  public void removeProductItem_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(result.id())))
      .then(CONFLICT);
  }

  @Test
  public void removeProductItem_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> "%s?price=%s&quantity=%s".formatted(product.getProductId(), product.getUnitPrice(), product.getQuantity()))
      .when(DELETE("/%s/products".formatted(result.id())))
      .then(CONFLICT);
  }
}
