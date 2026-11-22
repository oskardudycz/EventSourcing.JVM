package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.CancelOrder;
import static io.eventdriven.ecommerce.orders.OrderCommand.RetryOrderPayment;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.orders.getcustomerorders.CustomerOrder;
import io.eventdriven.ecommerce.orders.getcustomerorders.CustomerOrders;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatus;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatuses;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/customers/{customerId}/orders")
@Tag(
        name = "Orders",
        description = "The customer's orders: their status, canceling, paying again and the checkout page of a payment")
public class OrdersController {
    private final OrderFacade orders;
    private final PaymentFacade payments;
    private final CustomerOrders customerOrders;
    private final OrderStatuses orderStatuses;

    public OrdersController(
            OrderFacade orders, PaymentFacade payments, CustomerOrders customerOrders, OrderStatuses orderStatuses) {
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
        orders.cancelOrder(
                new CancelOrder(statusOf(customerId, orderId).getOrderId(), Order.CancellationReason.Requested));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{orderId}/retry-payment")
    @Operation(operationId = "RetryOrderPayment")
    public ResponseEntity<Void> retryPayment(@PathVariable UUID customerId, @PathVariable String orderId) {
        orders.retryOrderPayment(
                new RetryOrderPayment(statusOf(customerId, orderId).getOrderId()));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{orderId}/payments/{paymentId}/checkout-session")
    @Operation(operationId = "CreateCheckoutSession")
    public ResponseEntity<Void> openCheckout(
            @PathVariable UUID customerId, @PathVariable String orderId, @PathVariable String paymentId) {
        var order = statusOf(customerId, orderId);
        var orderStatusPage = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/shop/customers/{customerId}/orders/{orderId}")
                .buildAndExpand(customerId, orderId)
                .toUri();

        var session = payments.createCheckoutSession(new PaymentFacade.CreateCheckoutSession(
                new Payment.Id(Urns.of("ecommerce", "payment", paymentId)),
                order.getOrderId().value(),
                orderStatusPage,
                orderStatusPage));

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(session.url())
                .build();
    }

    private OrderStatus statusOf(UUID customerId, String orderId) {
        return orderStatuses
                .get(new Order.Id(Urns.of("ecommerce", "order", orderId)))
                .filter(status -> status.getCustomerId().equals(customerId))
                .orElseThrow(() -> new NoSuchElementException("Order '%s' was not found".formatted(orderId)));
    }
}
