package io.eventdriven.distributedprocesses.ecommerce.api;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GetCustomerOrdersTests extends ApiSpecification {
  private final UUID customerId = UUID.randomUUID();

  public GetCustomerOrdersTests() {
    super("customers");
  }

  @Test
  public void getCustomerOrders_succeeds_withTheNewestOrderFirst() {
    PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).paid());
    PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).checkedOut());

    given(() -> "")
      .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
      .then(response -> {
        OK.accept(response);
        var orders = (JsonNode) response.getResponseBody();
        assertEquals(2, orders.size());
        assertEquals("urn:ecommerce:order:%s-2".formatted(customerId), orders.get(0).get("orderId").asString());
        assertEquals("AwaitingPayment", orders.get(0).get("progress").asString());
        assertEquals("urn:ecommerce:order:%s-1".formatted(customerId), orders.get(1).get("orderId").asString());
        assertEquals("Completed", orders.get(1).get("progress").asString());
      });
  }

  @Test
  public void getCustomerOrders_succeeds_withNoOrders_forCustomerWhoNeverCheckedOut() {
    given(() -> "")
      .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
      .then(response -> {
        OK.accept(response);
        assertEquals(0, ((JsonNode) response.getResponseBody()).size());
      });
  }
}
