package io.eventdriven.ecommerce.api.controller.builders;

import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import io.eventdriven.ecommerce.core.http.ETag;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingCartRestBuilder {
  private final String apiPrefix = "api/shopping-carts/";
  private final TestRestTemplate restTemplate;
  private final int port;
  private UUID clientId;

  public String getApiUrl() {
    return "http://localhost:%s/%s".formatted(port, apiPrefix);
  }

  private ShoppingCartRestBuilder(TestRestTemplate restTemplate, int port) {
    this.restTemplate = restTemplate;
    this.port = port;
  }

  public static ShoppingCartRestBuilder of(TestRestTemplate restTemplate, int port, Function<ShoppingCartRestBuilder, ShoppingCartRestBuilder> with) {
    return with.apply(new ShoppingCartRestBuilder(restTemplate, port));
  }

  public ShoppingCartRestBuilder withClientId(UUID clientId) {
    this.clientId = clientId;

    return this;
  }

  public BuilderResult execute() {
    BuilderResult result = openShoppingCart();

    return result;
  }

  private BuilderResult openShoppingCart() {
    var response = this.restTemplate
      .postForEntity(getApiUrl(), new ShoppingCartsRequests.Open(clientId), Void.class);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());

    var locationHeader = response.getHeaders().getLocation();

    assertNotNull(locationHeader);

    var location = locationHeader.toString();

    assertTrue(location.startsWith(apiPrefix));
    var newId = assertDoesNotThrow(() -> UUID.fromString(location.substring(apiPrefix.length())));

    var eTag = response.getHeaders().getETag();

    return new BuilderResult(newId, new ETag(eTag));
  }

  public record BuilderResult(
    UUID id,
    ETag eTag
  ){}
}
