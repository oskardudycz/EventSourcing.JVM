package io.eventdriven.distributedprocesses.ecommerce.api;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.testing.ApiSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfigurations.MovableClock.class)
public class RetryOrderPaymentTests extends ApiSpecification {
  @Autowired
  private TestConfigurations.Clock clock;
  @Autowired
  private ScheduledPaymentTimeoutWorker paymentTimeoutWorker;

  private final UUID customerId = UUID.randomUUID();

  public RetryOrderPaymentTests() {
    super("customers");
  }

  @Test
  public void retryOrderPayment_succeeds_withANewPaymentToPay_afterThePaymentTimedOut() {
    var order = PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).checkedOut());
    clock.moveBy(Duration.ofHours(25));
    paymentTimeoutWorker.handleAll();
    assertEquals("PaymentFailed", statusOf(order.orderId()).get("progress").asString());

    given(() -> null)
      .when(POST("/%s/orders/%s/retry-payment".formatted(customerId, order.orderId())))
      .then(ACCEPTED);

    var status = statusOf(order.orderId());
    assertEquals("AwaitingPayment", status.get("progress").asString());
    assertEquals("urn:ecommerce:payment:%s-2".formatted(order.orderId()), status.get("paymentId").asString());
    assertEquals("PayNow", status.get("actions").get(0).asString());
  }

  @Test
  public void retryOrderPayment_succeeds_withAcceptedButChangesNothing_forPendingPayment() {
    var order = PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).checkedOut());

    given(() -> null)
      .when(POST("/%s/orders/%s/retry-payment".formatted(customerId, order.orderId())))
      .then(ACCEPTED);

    assertEquals("urn:ecommerce:payment:" + order.paymentId(), statusOf(order.orderId()).get("paymentId").asString());
  }

  @Test
  public void retryOrderPayment_fails_withNotFound_forUnknownOrder() {
    given(() -> null)
      .when(POST("/%s/orders/%s-1/retry-payment".formatted(customerId, UUID.randomUUID())))
      .then(NOT_FOUND);
  }

  private JsonNode statusOf(String orderId) {
    return GET("/%s/orders".formatted(customerId), JsonNode.class).apply(restTestClient, orderId).getResponseBody();
  }
}
