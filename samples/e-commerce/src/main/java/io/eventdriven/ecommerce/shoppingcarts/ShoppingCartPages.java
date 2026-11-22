package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;

import io.eventdriven.core.projections.LongPolling;
import io.eventdriven.ecommerce.shop.DemoProducts;
import io.eventdriven.ecommerce.shoppingcarts.currentcart.CustomerShoppingCarts;
import io.eventdriven.ecommerce.shoppingcarts.getcart.Cart;
import io.eventdriven.ecommerce.shoppingcarts.getcart.Carts;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shop")
public class ShoppingCartPages {
    private static final String WRITTEN_VERSION = "writtenVersion";

    public record ProductOffer(DemoProducts.Product product, double unitPrice) {}

    private final ShoppingCartFacade shoppingCarts;
    private final CustomerShoppingCarts customerShoppingCarts;
    private final Carts carts;
    private final ProductPriceCalculator prices;

    public ShoppingCartPages(
            ShoppingCartFacade shoppingCarts,
            CustomerShoppingCarts customerShoppingCarts,
            Carts carts,
            ProductPriceCalculator prices) {
        this.shoppingCarts = shoppingCarts;
        this.customerShoppingCarts = customerShoppingCarts;
        this.carts = carts;
        this.prices = prices;
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
        return "shoppingcarts/products";
    }

    @PostMapping("/customers/{customerId}/cart/items")
    public String addToCart(
            @PathVariable UUID customerId,
            @RequestParam UUID productId,
            @RequestParam int quantity,
            RedirectAttributes redirect) {
        var version = shoppingCarts.addProductItem(customerId, new ProductItem(productId, quantity));
        redirect.addFlashAttribute(WRITTEN_VERSION, version);
        return "redirect:/shop/customers/%s/cart".formatted(customerId);
    }

    @GetMapping("/customers/{customerId}/cart")
    public String cart(@PathVariable UUID customerId, Model model) {
        var written = model.getAttribute(WRITTEN_VERSION) instanceof Long version ? version : Long.MIN_VALUE;
        model.addAttribute("customerId", customerId);
        model.addAttribute(
                "cart",
                LongPolling.retryUntil(
                                () -> cartOf(customerId), cart -> cart.getVersion() >= written, LongPolling.DEADLINE)
                        .filter(cart -> !cart.getLines().isEmpty())
                        .orElse(null));
        model.addAttribute("productNames", DemoProducts.names());
        return "shoppingcarts/cart";
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
                redirect.addFlashAttribute(
                        WRITTEN_VERSION,
                        shoppingCarts.removeProductItem(new RemoveProductItemFromShoppingCart(
                                cartId, new ProductItem(productId, quantity), version)));
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
            redirect.addFlashAttribute(
                    WRITTEN_VERSION, shoppingCarts.checkOut(new CheckOutShoppingCart(cartId, version)));
        } catch (ConcurrentModificationException | IllegalStateException failed) {
            redirect.addFlashAttribute("message", "Your cart has changed. Check it and try again.");
            return "redirect:/shop/customers/%s/cart".formatted(customerId);
        }

        if (!shoppingCarts.isCheckedOut(cartId)) {
            redirect.addFlashAttribute(
                    "message", "Some prices have changed. Check the new prices and check out again.");
            return "redirect:/shop/customers/%s/cart".formatted(customerId);
        }

        return "redirect:/shop/customers/%s/orders/%s".formatted(customerId, cartId.tail());
    }

    private Optional<Cart> cartOf(UUID customerId) {
        return customerShoppingCarts.currentOf(customerId).flatMap(carts::get);
    }
}
