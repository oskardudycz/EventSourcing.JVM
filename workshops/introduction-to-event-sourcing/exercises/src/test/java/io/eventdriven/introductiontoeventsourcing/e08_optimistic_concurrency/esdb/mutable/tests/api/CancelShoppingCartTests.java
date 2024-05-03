package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.tests.api;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.core.http.ETag;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.ECommerceApplication;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.mutable.tests.api.builders.ShoppingCartRestBuilder;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.esdb.testing.ApiSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CancelShoppingCartTests extends ApiSpecification {
  public final UUID clientId = UUID.randomUUID();
  private UUID shoppingCartId;
  private ETag eTag;

  public CancelShoppingCartTests() {
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
  public void cancel_succeeds_forValidDataAndExistingShoppingCart() {
    given(() -> shoppingCartId)
      .when(DELETE(eTag))
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
      .when(DELETE(result.eTag()))
      .then(OK);
  }

  @Test
  public void cancel_fails_withMethodNotAllowed_forMissingShoppingCartId() {
    given(() -> "")
      .when(DELETE(eTag))
      .then(METHOD_NOT_ALLOWED);
  }

  @Test
  public void cancel_fails_withNotFound_forNotExistingShoppingCart() {
    var notExistingId = UUID.randomUUID();

    given(() -> notExistingId)
      .when(DELETE(eTag))
      .then(NOT_FOUND);
  }

  @Test
  public void cancel_fails_withConflict_forConfirmedShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(cart -> cart.withClientId(clientId).confirmed());

    given(() -> result.id())
      .when(DELETE(result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void cancel_fails_withConflict_forCanceledShoppingCart() {
    var result =
      ShoppingCartRestBuilder.of(restTemplate, port)
        .build(builder -> builder.withClientId(clientId).canceled());

    given(() -> result.id())
      .when(DELETE(result.eTag()))
      .then(CONFLICT);
  }

  @Test
  public void cancel_fails_withPreconditionFailed_forWrongETag() {
    var wrongETag = ETag.weak(999);

    given(() -> shoppingCartId)
      .when(DELETE(wrongETag))
      .then(PRECONDITION_FAILED);
  }
}
