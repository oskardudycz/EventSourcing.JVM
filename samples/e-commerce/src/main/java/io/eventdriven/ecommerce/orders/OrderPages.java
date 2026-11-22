package io.eventdriven.ecommerce.orders;

import static io.eventdriven.ecommerce.orders.OrderCommand.CancelOrder;
import static io.eventdriven.ecommerce.orders.OrderCommand.RetryOrderPayment;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.orders.getcustomerorders.CustomerOrders;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderAction;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatus;
import io.eventdriven.ecommerce.orders.getorderstatus.OrderStatuses;
import io.eventdriven.ecommerce.shop.DemoProducts;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/shop")
public class OrderPages {
    private final OrderFacade orders;
    private final CustomerOrders customerOrders;
    private final OrderStatuses orderStatuses;

    public OrderPages(OrderFacade orders, CustomerOrders customerOrders, OrderStatuses orderStatuses) {
        this.orders = orders;
        this.customerOrders = customerOrders;
        this.orderStatuses = orderStatuses;
    }

    @GetMapping("/customers/{customerId}/orders")
    public String orders(@PathVariable UUID customerId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute("orders", customerOrders.of(customerId));
        return "orders/orders";
    }

    @GetMapping("/customers/{customerId}/orders/{orderId}")
    public String orderStatus(@PathVariable UUID customerId, @PathVariable String orderId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute("orderTail", orderId);

        if (orderStatusOf(orderId).isEmpty()) return "orders/order-placing";

        var status = statusOf(customerId, orderId);
        model.addAttribute("order", status);
        model.addAttribute("paymentTail", status.getPaymentId().tail());
        model.addAttribute("canPayNow", status.getActions().contains(OrderAction.PayNow));
        model.addAttribute("canRetryPayment", status.getActions().contains(OrderAction.RetryPayment));
        model.addAttribute("canCancel", status.getActions().contains(OrderAction.CancelOrder));
        model.addAttribute("productNames", DemoProducts.names());
        return "orders/order-status";
    }

    @PostMapping("/customers/{customerId}/orders/{orderId}/cancel")
    public String cancel(@PathVariable UUID customerId, @PathVariable String orderId) {
        orders.cancelOrder(
                new CancelOrder(statusOf(customerId, orderId).getOrderId(), Order.CancellationReason.Requested));
        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
    }

    @PostMapping("/customers/{customerId}/orders/{orderId}/retry-payment")
    public String retryPayment(@PathVariable UUID customerId, @PathVariable String orderId) {
        orders.retryOrderPayment(
                new RetryOrderPayment(statusOf(customerId, orderId).getOrderId()));
        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
    }

    private Optional<OrderStatus> orderStatusOf(String orderId) {
        return orderStatuses.get(new Order.Id(Urns.of("ecommerce", "order", orderId)));
    }

    private OrderStatus statusOf(UUID customerId, String orderId) {
        return orderStatusOf(orderId)
                .filter(status -> status.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
