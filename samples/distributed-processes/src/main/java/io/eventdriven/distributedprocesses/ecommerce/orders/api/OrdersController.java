package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.CancelOrder;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.RetryOrderPayment;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.CreateCheckoutSession;

@RestController
@RequestMapping("/customers/{customerId}/orders")
@Tag(name = "Orders", description = "The customer's orders: their status, canceling, paying again and the checkout page of a payment")
public class OrdersController {
  private final OrderFacade orders;
  private final PaymentFacade payments;
  private final CustomerOrders customerOrders;
  private final OrderStatuses orderStatuses;

  public OrdersController(
    OrderFacade orders,
    PaymentFacade payments,
    CustomerOrders customerOrders,
    OrderStatuses orderStatuses
  ) {
    this.orders = orders;
    this.payments = payments;
    this.customerOrders = customerOrders;
    this.orderStatuses = orderStatuses;
  }

  @GetMapping
  @Operation(operationId = "CustomerOrders")
  public List<CustomerOrder> list(@PathVariable UUID customerId) {
    return customerOrders.of(customerId);
  }

  @GetMapping("/{orderId}")
  @Operation(operationId = "OrderStatus")
  public OrderStatus status(@PathVariable UUID customerId, @PathVariable String orderId) {
    return statusOf(customerId, orderId);
  }

  @PostMapping("/{orderId}/cancel")
  @Operation(operationId = "CancelOrder")
  public ResponseEntity<Void> cancel(@PathVariable UUID customerId, @PathVariable String orderId) {
    orders.cancelOrder(new CancelOrder(statusOf(customerId, orderId).getOrderId(), OrderCancellationReason.Requested));

    return ResponseEntity.accepted().build();
  }

  @PostMapping("/{orderId}/retry-payment")
  @Operation(operationId = "RetryOrderPayment")
  public ResponseEntity<Void> retryPayment(@PathVariable UUID customerId, @PathVariable String orderId) {
    orders.retryOrderPayment(new RetryOrderPayment(statusOf(customerId, orderId).getOrderId()));

    return ResponseEntity.accepted().build();
  }

  @PostMapping("/{orderId}/payments/{paymentId}/checkout-session")
  @Operation(operationId = "CreateCheckoutSession")
  public ResponseEntity<Void> openCheckout(
    @PathVariable UUID customerId,
    @PathVariable String orderId,
    @PathVariable String paymentId
  ) {
    var order = statusOf(customerId, orderId);
    var orderStatusPage = ServletUriComponentsBuilder.fromCurrentContextPath()
      .path("/shop/customers/{customerId}/orders/{orderId}")
      .buildAndExpand(customerId, orderId)
      .toUri();

    var session = payments.createCheckoutSession(new CreateCheckoutSession(
      new PaymentId(Urns.of("ecommerce", "payment", paymentId)),
      order.getOrderId().value(),
      orderStatusPage,
      orderStatusPage
    ));

    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(session.url()).build();
  }

  private OrderStatus statusOf(UUID customerId, String orderId) {
    return orderStatuses.get(new OrderId(Urns.of("ecommerce", "order", orderId)))
      .filter(status -> status.getCustomerId().equals(customerId))
      .orElseThrow(() -> new NoSuchElementException("Order '%s' was not found".formatted(orderId)));
  }
}
