package io.eventdriven.distributedprocesses.ecommerce.api.builders;

import io.eventdriven.distributedprocesses.core.http.ETag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PurchaseRestBuilder {
  public record Product(UUID productId, int quantity) {
  }

  public record BuilderResult(
    UUID customerId,
    ETag cartETag,
    String orderId,
    String paymentId
  ) {
  }

  private final RestTestClient restTestClient;
  private final int port;
  private UUID customerId = UUID.randomUUID();
  private final List<Product> products = new ArrayList<>();
  private boolean isCheckedOut;
  private boolean isPaid;

  private PurchaseRestBuilder(RestTestClient restTestClient, int port) {
    this.restTestClient = restTestClient;
    this.port = port;
  }

  public static PurchaseRestBuilder of(RestTestClient restTestClient, int port) {
    return new PurchaseRestBuilder(restTestClient, port);
  }

  public PurchaseRestBuilder withCustomerId(UUID customerId) {
    this.customerId = customerId;

    return this;
  }

  public PurchaseRestBuilder withProduct(UUID productId, int quantity) {
    this.products.add(new Product(productId, quantity));

    return this;
  }

  public PurchaseRestBuilder checkedOut() {
    this.isCheckedOut = true;

    return this;
  }

  public PurchaseRestBuilder paid() {
    this.isCheckedOut = true;
    this.isPaid = true;

    return this;
  }

  public BuilderResult build(Consumer<PurchaseRestBuilder> with) {
    with.accept(this);

    return execute();
  }

  public BuilderResult execute() {
    ETag eTag = null;

    for (var product : products) {
      eTag = addProduct(product);
    }

    var cart = currentCartOf(customerId);
    var orderId = cart.substring(cart.lastIndexOf(':') + 1);

    if (isCheckedOut)
      checkOut(eTag);

    if (isPaid)
      pay(orderId, orderId);

    return new BuilderResult(customerId, eTag, orderId, orderId);
  }

  public String openCheckout(String orderId, String paymentId) {
    var response = restTestClient
      .post()
      .uri(apiUrl("customers/%s/orders/%s/payments/%s/checkout-session".formatted(customerId, orderId, paymentId)))
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
    assertNotNull(response.getResponseHeaders().getLocation());

    return response.getResponseHeaders().getLocation().toString();
  }

  public String payOn(String checkoutPage, String cardNumber) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("cardNumber", cardNumber);

    var response = restTestClient
      .post()
      .uri(apiUrl(checkoutPage.substring(1)))
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.SEE_OTHER, response.getStatus());

    return response.getResponseHeaders().getLocation().toString();
  }

  private void pay(String orderId, String paymentId) {
    payOn(openCheckout(orderId, paymentId), "4242 4242 4242 4242");
  }

  private ETag addProduct(Product product) {
    var response = restTestClient
      .post()
      .uri(apiUrl("customers/%s/shopping-carts/current/product-items".formatted(customerId)))
      .contentType(MediaType.APPLICATION_JSON)
      .body(product)
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.OK, response.getStatus());

    return new ETag(response.getResponseHeaders().getETag());
  }

  private String currentCartOf(UUID customerId) {
    var response = restTestClient
      .get()
      .uri(apiUrl("customers/%s/shopping-carts/current".formatted(customerId)))
      .exchange()
      .returnResult(JsonNode.class);

    assertEquals(HttpStatus.OK, response.getStatus());

    return response.getResponseBody().get("shoppingCartId").asString();
  }

  private void checkOut(ETag eTag) {
    var response = restTestClient
      .post()
      .uri(apiUrl("customers/%s/shopping-carts/current/checkout".formatted(customerId)))
      .header("If-Match", eTag.value())
      .exchange()
      .returnResult(Void.class);

    assertEquals(HttpStatus.ACCEPTED, response.getStatus());
  }

  private String apiUrl(String path) {
    return "http://localhost:%s/%s".formatted(port, path);
  }
}
