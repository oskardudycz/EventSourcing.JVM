package io.eventdriven.distributedprocesses.ecommerce.api;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PurchaseOverHttpTests extends ApiSpecification {
  private final UUID customerId = UUID.randomUUID();

  public PurchaseOverHttpTests() {
    super("customers");
  }

  @Test
  public void aCustomerChecksOutPaysOnTheGatewaysPageAndTheSandboxWarehouseShipsTheOrder() {
    var builder = PurchaseRestBuilder.of(restTestClient, port);
    var order = builder.build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 2).checkedOut());

    var checkoutPage = builder.openCheckout(order.orderId(), order.paymentId());
    var backToTheShop = builder.payOn(checkoutPage, "4242 4242 4242 4242");

    assertTrue(backToTheShop.endsWith("/shop/customers/%s/orders/%s".formatted(customerId, order.orderId())));

    given(() -> order.orderId())
      .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
      .then(response -> {
        OK.accept(response);
        var status = (JsonNode) response.getResponseBody();
        assertEquals("Completed", status.get("progress").asString());
        assertEquals("Sandbox Express", status.get("carrier").asString());
      });

    given(() -> "")
      .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
      .then(response -> assertEquals("Completed", ((JsonNode) response.getResponseBody()).get(0).get("progress").asString()));
  }
}
