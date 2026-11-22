package io.eventdriven.ecommerce.shop;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/shop")
public class ShopPages {
    private static final String customerCookie = "customer";

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
    public String logOut(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();

        var cookie = new Cookie(customerCookie, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "redirect:/shop/login";
    }

    private static UUID customerIdOf(String name) {
        return UUID.nameUUIDFromBytes(name.strip().toLowerCase(Locale.ROOT).getBytes(UTF_8));
    }

    @ControllerAdvice(basePackages = "io.eventdriven.ecommerce")
    public static class Layout {
        private final String grafanaUrl;

        public Layout(@Value("${shop.grafana-url:http://localhost:3000}") String grafanaUrl) {
            this.grafanaUrl = grafanaUrl;
        }

        @ModelAttribute("grafanaUrl")
        public String grafanaUrl() {
            return grafanaUrl;
        }

        @ModelAttribute("customerName")
        public @Nullable String customerName(
                @CookieValue(name = customerCookie, required = false) @Nullable String customer) {
            return customer != null ? URLDecoder.decode(customer, UTF_8) : null;
        }
    }
}
