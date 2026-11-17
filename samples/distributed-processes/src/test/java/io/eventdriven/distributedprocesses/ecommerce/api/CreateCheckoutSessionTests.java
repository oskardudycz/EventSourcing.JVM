package io.eventdriven.distributedprocesses.ecommerce.api;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateCheckoutSessionTests extends ApiSpecification {
  private final UUID customerId = UUID.randomUUID();

  public CreateCheckoutSessionTests() {
    super("customers");
  }

  @Test
  public void createCheckoutSession_succeeds_withSeeOtherToTheGatewaysPage() {
    var order = checkedOutOrder();

    given(() -> null)
      .when(POST(checkoutSession(order)))
      .then(response -> {
        SEE_OTHER.accept(response);
        assertTrue(response.getResponseHeaders().getLocation().toString().startsWith("/sandbox/checkout/"));
      });
  }

  @Test
  public void createCheckoutSession_succeeds_withTheSameSession_whileItIsOpen() {
    var order = checkedOutOrder();
    var first = POST(checkoutSession(order)).apply(restTestClient, null).getResponseHeaders().getLocation();

    given(() -> null)
      .when(POST(checkoutSession(order)))
      .then(response -> {
        SEE_OTHER.accept(response);
        assertEquals(first, response.getResponseHeaders().getLocation());
      });
  }

  @Test
  public void createCheckoutSession_fails_withConflict_forPaidPayment() {
    var order = PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).paid());

    given(() -> null)
      .when(POST(checkoutSession(order)))
      .then(CONFLICT);
  }

  @Test
  public void createCheckoutSession_fails_withNotFound_forPaymentOfAnotherOrder() {
    var order = checkedOutOrder();
    var other = PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withProduct(UUID.randomUUID(), 1).checkedOut());

    given(() -> null)
      .when(POST("/%s/orders/%s/payments/%s/checkout-session".formatted(customerId, order.orderId(), other.paymentId())))
      .then(NOT_FOUND);
  }

  @Test
  public void createCheckoutSession_fails_withNotFound_forUnknownOrder() {
    var unknown = "%s-1".formatted(UUID.randomUUID());

    given(() -> null)
      .when(POST("/%s/orders/%s/payments/%s/checkout-session".formatted(customerId, unknown, unknown)))
      .then(NOT_FOUND);
  }

  private PurchaseRestBuilder.BuilderResult checkedOutOrder() {
    return PurchaseRestBuilder.of(restTestClient, port)
      .build(purchase -> purchase.withCustomerId(customerId).withProduct(UUID.randomUUID(), 1).checkedOut());
  }

  private String checkoutSession(PurchaseRestBuilder.BuilderResult order) {
    return "/%s/orders/%s/payments/%s/checkout-session".formatted(customerId, order.orderId(), order.paymentId());
  }
}
