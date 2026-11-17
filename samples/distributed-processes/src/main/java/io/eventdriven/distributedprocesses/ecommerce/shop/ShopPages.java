package io.eventdriven.distributedprocesses.ecommerce.shop;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.CancelOrder;
import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.RetryOrderPayment;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;

@Controller
@RequestMapping("/shop")
public class ShopPages {
  public record ProductOffer(DemoProducts.Product product, double unitPrice) {
  }

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
    OrderStatuses orderStatuses
  ) {
    this.shoppingCarts = shoppingCarts;
    this.customerShoppingCarts = customerShoppingCarts;
    this.carts = carts;
    this.prices = prices;
    this.orders = orders;
    this.customerOrders = customerOrders;
    this.orderStatuses = orderStatuses;
  }

  @GetMapping
  public String start() {
    return "redirect:/shop/customers/%s/products".formatted(UUID.randomUUID());
  }

  @GetMapping("/customers/{customerId}/products")
  public String products(@PathVariable UUID customerId, Model model) {
    model.addAttribute("customerId", customerId);
    model.addAttribute("cartSize", cartOf(customerId).map(cart -> cart.getLines().size()).orElse(0));
    model.addAttribute("offers", DemoProducts.all().stream()
      .map(product -> new ProductOffer(product, prices.calculate(new ProductItem(product.id(), 1)).unitPrice()))
      .toList());
    return "shop/products";
  }

  @PostMapping("/customers/{customerId}/cart/items")
  public String addToCart(@PathVariable UUID customerId, @RequestParam UUID productId, @RequestParam int quantity) {
    shoppingCarts.addProductItem(new AddProductItemToShoppingCart(customerId, new ProductItem(productId, quantity)));
    return "redirect:/shop/customers/%s/cart".formatted(customerId);
  }

  @GetMapping("/customers/{customerId}/cart")
  public String cart(@PathVariable UUID customerId, Model model) {
    model.addAttribute("customerId", customerId);
    model.addAttribute("cart", cartOf(customerId).filter(cart -> !cart.getLines().isEmpty()).orElse(null));
    model.addAttribute("productNames", DemoProducts.names());
    return "shop/cart";
  }

  @PostMapping("/customers/{customerId}/cart/items/{productId}/remove")
  public String removeFromCart(
    @PathVariable UUID customerId,
    @PathVariable UUID productId,
    @RequestParam int quantity,
    @RequestParam long version,
    RedirectAttributes redirect
  ) {
    customerShoppingCarts.currentOf(customerId).ifPresent(cartId -> {
      try {
        shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(cartId, new ProductItem(productId, quantity), version));
      } catch (ConcurrentModificationException | IllegalStateException failed) {
        redirect.addFlashAttribute("message", "Your cart has changed. Check it and try again.");
      }
    });
    return "redirect:/shop/customers/%s/cart".formatted(customerId);
  }

  @PostMapping("/customers/{customerId}/cart/checkout")
  public String checkOut(@PathVariable UUID customerId, @RequestParam long version, RedirectAttributes redirect) {
    var cartId = customerShoppingCarts.currentOf(customerId).orElse(null);
    if (cartId == null)
      return "redirect:/shop/customers/%s/cart".formatted(customerId);

    try {
      shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, version));
    } catch (ConcurrentModificationException | IllegalStateException failed) {
      redirect.addFlashAttribute("message", "Your cart has changed. Check it and try again.");
      return "redirect:/shop/customers/%s/cart".formatted(customerId);
    }

    if (!carts.get(cartId).map(Cart::isCheckedOut).orElse(false)) {
      redirect.addFlashAttribute("message", "Some prices have changed. Check the new prices and check out again.");
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
    orders.cancelOrder(new CancelOrder(statusOf(customerId, orderId).getOrderId(), OrderCancellationReason.Requested));
    return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
  }

  @PostMapping("/customers/{customerId}/orders/{orderId}/retry-payment")
  public String retryPayment(@PathVariable UUID customerId, @PathVariable String orderId) {
    orders.retryOrderPayment(new RetryOrderPayment(statusOf(customerId, orderId).getOrderId()));
    return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, orderId);
  }

  private Optional<Cart> cartOf(UUID customerId) {
    return customerShoppingCarts.currentOf(customerId).flatMap(carts::get);
  }

  private OrderStatus statusOf(UUID customerId, String orderId) {
    return orderStatuses.get(new OrderId(Urns.of("ecommerce", "order", orderId)))
      .filter(status -> status.getCustomerId().equals(customerId))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
  }
}
