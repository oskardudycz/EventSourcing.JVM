package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.tests.api.builders;

import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.api.ShoppingCartsRequests.AddProduct;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.api.ShoppingCartsRequests.Open;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.mongodb.immutable.app.api.ShoppingCartsRequests.ProductItemRequest;
import io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.core.http.ETag;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingCartRestBuilder {
  private final String apiPrefix = "api/shopping-carts";
  private final RestTestClient restTestClient;
  private final int port;
  private UUID clientId;
  private boolean isConfirmed;
  private boolean isCanceled;
  private final List<ProductItemRequest> products = new ArrayList<>();

  public String getApiUrl() {
    return "http://localhost:%s/%s".formatted(port, apiPrefix);
  }

  private ShoppingCartRestBuilder(RestTestClient restTestClient, int port) {
    this.restTestClient = restTestClient;
    this.port = port;
  }

  public static ShoppingCartRestBuilder of(RestTestClient restTestClient, int port) {
    return new ShoppingCartRestBuilder(restTestClient, port);
  }

  public ShoppingCartRestBuilder withClientId(UUID clientId) {
    this.clientId = clientId;

    return this;
  }

  public ShoppingCartRestBuilder withProduct(ProductItemRequest product) {
    this.products.add(product);

    return this;
  }

  public ShoppingCartRestBuilder confirmed() {
    this.isConfirmed = true;

    return this;
  }

  public ShoppingCartRestBuilder canceled() {
    this.isCanceled = true;

    return this;
  }

  public BuilderResult build(Consumer<ShoppingCartRestBuilder> with) {
    with.accept(this);

    return execute();
  }

  public BuilderResult execute() {
    BuilderResult result = open();

    if (isConfirmed) {
      result = confirm(result);
    }

    if (isCanceled) {
      result = cancel(result);
    }

    for (var product: products){
      result = addProduct(product, result);
    }

    return result;
  }

  private BuilderResult open() {
    var response = this.restTestClient
      .post()
      .uri(getApiUrl())
      .body(new Open(clientId))
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.CREATED, response.getStatus());

    var locationHeader = response.getResponseHeaders().getLocation();

    assertNotNull(locationHeader);

    var location = locationHeader.toString();

    assertTrue(location.startsWith(apiPrefix));
    var newId = assertDoesNotThrow(() -> UUID.fromString(location.substring(apiPrefix.length() + 1)));

    return getResult(response, newId);
  }

  private BuilderResult addProduct(ProductItemRequest product, BuilderResult result) {
    var response = this.restTestClient
      .post()
      .uri(getApiUrl() + "/%s/products".formatted(result.id()))
      .headers(headers -> {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setIfMatch(result.eTag().value());
      })
      .body(new AddProduct(product))
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.OK, response.getStatus());
    return getResult(response, result.id());
  }


  private BuilderResult confirm(BuilderResult result) {
    var response = this.restTestClient
      .put()
      .uri("%s/%s".formatted(getApiUrl(), result.id()))
      .headers(headers -> {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setIfMatch(result.eTag().value());
      })
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.OK, response.getStatus());
    return getResult(response, result.id());
  }

  private BuilderResult cancel(BuilderResult result) {
    var response = this.restTestClient
      .delete()
      .uri("%s/%s".formatted(getApiUrl(), result.id()))
      .headers(headers -> {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setIfMatch(result.eTag().value());
      })
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.OK, response.getStatus());
    return getResult(response, result.id());
  }

  private BuilderResult getResult(EntityExchangeResult<Void> response, UUID newId) {
    var eTag = response.getResponseHeaders().getETag();

    return new BuilderResult(newId, new ETag(eTag));
  }

  public record BuilderResult(
    UUID id,
    ETag eTag
  ) {
  }
}
