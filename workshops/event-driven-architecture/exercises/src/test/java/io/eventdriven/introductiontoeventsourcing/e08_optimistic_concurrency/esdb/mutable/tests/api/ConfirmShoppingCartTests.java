package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.tests.api;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.http.ETag;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.ECommerceApplication;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.tests.api.builders.ShoppingCartRestBuilder;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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

  @Tag("Exercise")
  @Test
  public void confirm_succeeds_forValidDataAndExistingShoppingCart() {
    given(() -> shoppingCartId)
      .when(PUT(eTag))
      .then(OK);
  }

  @Tag("Exercise")
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

  @Tag("Exercise")
  @Test
  public void confirm_fails_withNotFound_forMissingShoppingCartId() {
    given(() -> "")
      .when(PUT(eTag))
      .then(NOT_FOUND);
  }

  @Tag("Exercise")
  @Test
  public void confirm_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> notExistingId)
      .when(PUT(eTag))
      .then(NOT_FOUND);
  }

  @Tag("Exercise")
  @Test
  public void confirm_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> result.id())
      .when(PUT(result.eTag()))
      .then(CONFLICT);
  }

  @Tag("Exercise")
  @Test
  public void confirm_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> result.id())
      .when(PUT(result.eTag()))
      .then(CONFLICT);
  }

  @Tag("Exercise")
  @Test
  public void confirm_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> shoppingCartId)
      .when(PUT(wrongETag))
      .then(PRECONDITION_FAILED);
  }
}
