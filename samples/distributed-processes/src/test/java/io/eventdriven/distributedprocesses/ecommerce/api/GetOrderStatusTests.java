package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

@ActiveProfiles("in-memory")
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
                    assertThat(status.get("progress").asString()).isEqualTo("AwaitingPayment");
                    assertThat(status.get("paymentId").asString())
                            .isEqualTo("urn:ecommerce:payment:" + order.paymentId());
                    assertThat(actionsOf(status)).isEqualTo(List.of("PayNow", "CancelOrder"));
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
                    assertThat(status.get("progress").asString()).isEqualTo("Completed");
                    assertThat(status.get("carrier").asString()).isEqualTo("Sandbox Express");
                    assertThat(status.get("trackingNumber").asString().startsWith("SBX"))
                            .isTrue();
                    assertThat(actionsOf(status)).isEqualTo(List.of());
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
