package io.eventdriven.distributedprocesses.ecommerce.shop;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.CancelOrder;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.RetryOrderPayment;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderAction;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCarts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Cart;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Carts;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shop")
public class ShopPages {
    public record ProductOffer(DemoProducts.Product product, double unitPrice) {}

    private static final String customerCookie = "customer";

    private final ShoppingCartFacade shoppingCarts;
    private final CustomerShoppingCarts customerShoppingCarts;
    private final Carts carts;
    private final ProductPriceCalculator prices;
    private final OrderFacade orders;
    private final CustomerOrders customerOrders;
    private final OrderStatuses orderStatuses;

    public ShopPages(
            ShoppingCartFacade shoppingCarts,
            CustomerShoppingCarts customerShoppingCarts,
            Carts carts,
            ProductPriceCalculator prices,
            OrderFacade orders,
            CustomerOrders customerOrders,
            OrderStatuses orderStatuses) {
        this.shoppingCarts = shoppingCarts;
        this.customerShoppingCarts = customerShoppingCarts;
        this.carts = carts;
        this.prices = prices;
        this.orders = orders;
        this.customerOrders = customerOrders;
        this.orderStatuses = orderStatuses;
    }

    @ModelAttribute("customerName")
    public @Nullable String customerName(
            @CookieValue(name = customerCookie, required = false) @Nullable String customer) {
        return customer != null ? URLDecoder.decode(customer, UTF_8) : null;
    }

    @GetMapping
    public String start(@ModelAttribute("customerName") @Nullable String customerName) {
        if (customerName == null) return "redirect:/shop/login";

        return "redirect:/shop/customers/%s/products".formatted(customerIdOf(customerName));
    }

    @GetMapping("/login")
    public String login() {
        return "shop/login";
    }

    @PostMapping("/login")
    public String logIn(@RequestParam String name, HttpServletResponse response) {
        if (name.isBlank()) return "redirect:/shop/login";

        var cookie = new Cookie(customerCookie, URLEncoder.encode(name.strip(), UTF_8));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        return "redirect:/shop/customers/%s/products".formatted(customerIdOf(name));
    }

    @PostMapping("/logout")
    public String logOut(HttpServletResponse response) {
        var cookie = new Cookie(customerCookie, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "redirect:/shop/login";
    }

    private static UUID customerIdOf(String name) {
        return UUID.nameUUIDFromBytes(name.strip().toLowerCase(Locale.ROOT).getBytes(UTF_8));
    }

    @GetMapping("/customers/{customerId}/products")
    public String products(@PathVariable UUID customerId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute(
                "cartSize",
                cartOf(customerId).map(cart -> cart.getLines().size()).orElse(0));
        model.addAttribute(
                "offers",
                DemoProducts.all().stream()
                        .map(product -> new ProductOffer(
                                product,
                                prices.calculate(new ProductItem(product.id(), 1))
                                        .unitPrice()))
                        .toList());
        return "shop/products";
    }

    @PostMapping("/customers/{customerId}/cart/items")
    public String addToCart(@PathVariable UUID customerId, @RequestParam UUID productId, @RequestParam int quantity) {
        shoppingCarts.addProductItem(
                new AddProductItemToShoppingCart(customerId, new ProductItem(productId, quantity)));
        return "redirect:/shop/customers/%s/cart".formatted(customerId);
    }

    @GetMapping("/customers/{customerId}/cart")
    public String cart(@PathVariable UUID customerId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute(
                "cart",
                cartOf(customerId).filter(cart -> !cart.getLines().isEmpty()).orElse(null));
        model.addAttribute("productNames", DemoProducts.names());
        return "shop/cart";
    }

    @PostMapping("/customers/{customerId}/cart/items/{productId}/remove")
    public String removeFromCart(
            @PathVariable UUID customerId,
            @PathVariable UUID productId,
            @RequestParam int quantity,
            @RequestParam long version,
            RedirectAttributes redirect) {
        customerShoppingCarts.currentOf(customerId).ifPresent(cartId -> {
            try {
                shoppingCarts.removeProductItem(
                        new RemoveProductItemFromShoppingCart(cartId, new ProductItem(productId, quantity), version));
            } catch (ConcurrentModificationException | IllegalStateException failed) {
                redirect.addFlashAttribute("message", "Your cart has changed. Check it and try again.");
            }
        });
        return "redirect:/shop/customers/%s/cart".formatted(customerId);
    }

    @PostMapping("/customers/{customerId}/cart/checkout")
    public String checkOut(@PathVariable UUID customerId, @RequestParam long version, RedirectAttributes redirect) {
        var cartId = customerShoppingCarts.currentOf(customerId).orElse(null);
        if (cartId == null) return "redirect:/shop/customers/%s/cart".formatted(customerId);

        try {
            shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, version));
        } catch (ConcurrentModificationException | IllegalStateException failed) {
            redirect.addFlashAttribute("message", "Your cart has changed. Check it and try again.");
            return "redirect:/shop/customers/%s/cart".formatted(customerId);
        }

        if (!carts.get(cartId).map(Cart::isCheckedOut).orElse(false)) {
            redirect.addFlashAttribute(
                    "message", "Some prices have changed. Check the new prices and check out again.");
            return "redirect:/shop/customers/%s/cart".formatted(customerId);
        }

        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, cartId.tail());
    }

    @GetMapping("/customers/{customerId}/orders")
    public String orders(@PathVariable UUID customerId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute("orders", customerOrders.of(customerId));
        return "shop/orders";
    }

    @GetMapping("/customers/{customerId}/orders/{orderId}")
    public String orderStatus(@PathVariable UUID customerId, @PathVariable String orderId, Model model) {
        var status = statusOf(customerId, orderId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("orderTail", orderId);
        model.addAttribute("order", status);
        model.addAttribute("paymentTail", status.getPaymentId().tail());
        model.addAttribute("canPayNow", status.getActions().contains(OrderAction.PayNow));
        model.addAttribute("canRetryPayment", status.getActions().contains(OrderAction.RetryPayment));
        model.addAttribute("canCancel", status.getActions().contains(OrderAction.CancelOrder));
        model.addAttribute("productNames", DemoProducts.names());
        return "shop/order-status";
    }

    @PostMapping("/customers/{customerId}/orders/{orderId}/cancel")
    public String cancel(@PathVariable UUID customerId, @PathVariable String orderId) {
        orders.cancelOrder(
                new CancelOrder(statusOf(customerId, orderId).getOrderId(), OrderCancellationReason.Requested));
        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
    }

    @PostMapping("/customers/{customerId}/orders/{orderId}/retry-payment")
    public String retryPayment(@PathVariable UUID customerId, @PathVariable String orderId) {
        orders.retryOrderPayment(
                new RetryOrderPayment(statusOf(customerId, orderId).getOrderId()));
        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
    }

    private Optional<Cart> cartOf(UUID customerId) {
        return customerShoppingCarts.currentOf(customerId).flatMap(carts::get);
    }

    private OrderStatus statusOf(UUID customerId, String orderId) {
        return orderStatuses
                .get(new OrderId(Urns.of("ecommerce", "order", orderId)))
                .filter(status -> status.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
