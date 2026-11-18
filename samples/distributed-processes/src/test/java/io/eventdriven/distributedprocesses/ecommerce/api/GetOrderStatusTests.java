package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GetOrderStatusTests extends ApiSpecification {
    private final UUID customerId = UUID.randomUUID();

    public GetOrderStatusTests() {
        super("customers");
    }

    @Test
    public void getOrderStatus_succeeds_withPayNowAndCancel_forUnpaidOrder() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 2)
                        .checkedOut());

        given(() -> order.orderId())
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    var status = (JsonNode) response.getResponseBody();
                    assertEquals("AwaitingPayment", status.get("progress").asString());
                    assertEquals(
                            "urn:ecommerce:payment:" + order.paymentId(),
                            status.get("paymentId").asString());
                    assertEquals(List.of("PayNow", "CancelOrder"), actionsOf(status));
                });
    }

    @Test
    public void getOrderStatus_succeeds_withTheCarrierAndNoActions_forPaidOrder() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(customerId)
                        .withProduct(UUID.randomUUID(), 2)
                        .paid());

        given(() -> order.orderId())
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(response -> {
                    OK.accept(response);
                    var status = (JsonNode) response.getResponseBody();
                    assertEquals("Completed", status.get("progress").asString());
                    assertEquals("Sandbox Express", status.get("carrier").asString());
                    assertTrue(status.get("trackingNumber").asString().startsWith("SBX"));
                    assertEquals(List.of(), actionsOf(status));
                });
    }

    @Test
    public void getOrderStatus_fails_withNotFound_forUnknownOrder() {
        given(() -> "%s-1".formatted(UUID.randomUUID()))
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(NOT_FOUND);
    }

    @Test
    public void getOrderStatus_fails_withNotFound_forAnotherCustomersOrder() {
        var order = PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withProduct(UUID.randomUUID(), 2).checkedOut());

        given(() -> order.orderId())
                .when(GET("/%s/orders".formatted(customerId), JsonNode.class))
                .then(NOT_FOUND);
    }

    private static List<String> actionsOf(JsonNode status) {
        return StreamSupport.stream(status.get("actions").spliterator(), false)
                .map(JsonNode::asString)
                .toList();
    }
}
