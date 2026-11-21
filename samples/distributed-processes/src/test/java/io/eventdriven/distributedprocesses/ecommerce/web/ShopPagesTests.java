package io.eventdriven.distributedprocesses.ecommerce.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.eventdriven.distributedprocesses.ecommerce.shop.DemoProducts;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("in-memory")
@SpringBootTest
@AutoConfigureMockMvc
public class ShopPagesTests {
    @Autowired
    private MockMvc mvc;

    private final UUID customerId = UUID.randomUUID();
    private final String shop = "/shop/customers/%s".formatted(customerId);
    private final String orderTail = "%s-1".formatted(customerId);
    private final DemoProducts.Product shoes = DemoProducts.all().getFirst();

    @Test
    public void aVisitorWhoIsNotLoggedInIsAskedForTheirName() throws Exception {
        mvc.perform(get("/shop")).andExpect(redirectedUrl("/shop/login"));

        mvc.perform(get("/shop/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your name")))
                .andExpect(content().string(containsString("Log in")));
    }

    @Test
    public void loggingInOpensTheProductsPageAndRemembersTheCustomer() throws Exception {
        mvc.perform(post("/shop/login").param("name", "Marty McFly"))
                .andExpect(redirectedUrlPattern("/shop/customers/*/products"))
                .andExpect(cookie().exists("customer"));
    }

    @Test
    public void theSameNameAlwaysLogsInAsTheSameCustomer() throws Exception {
        var first = logIn("Marty McFly");

        assertThat(logIn("Marty McFly")).isEqualTo(first);
        assertThat(logIn("Lorraine Baines")).isNotEqualTo(first);
    }

    @Test
    public void aLoggedInCustomerReturnsToTheirShop() throws Exception {
        var products = logIn("Marty McFly");

        mvc.perform(get("/shop").cookie(new Cookie("customer", "Marty%20McFly")))
                .andExpect(redirectedUrl(products));
    }

    @Test
    public void theHeaderShowsWhoIsLoggedInAndHowToLogOut() throws Exception {
        mvc.perform(get(shop + "/products").cookie(new Cookie("customer", "Marty%20McFly")))
                .andExpect(content().string(containsString("Marty McFly")))
                .andExpect(content().string(containsString("Log out")));
    }

    @Test
    public void loggingOutForgetsTheCustomer() throws Exception {
        mvc.perform(post("/shop/logout").cookie(new Cookie("customer", "Marty%20McFly")))
                .andExpect(redirectedUrl("/shop/login"))
                .andExpect(cookie().maxAge("customer", 0));
    }

    @Test
    public void theProductsPageOffersEveryDemoProduct() throws Exception {
        var page = mvc.perform(get(shop + "/products")).andExpect(status().isOk());

        for (var product : DemoProducts.all()) page.andExpect(content().string(containsString(product.name())));
        page.andExpect(content().string(containsString("action=\"%s/cart/items\"".formatted(shop))));
    }

    @Test
    public void anEmptyCartSaysSo() throws Exception {
        mvc.perform(get(shop + "/cart"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your cart is empty")));
    }

    @Test
    public void theWholePurchaseGoesThroughThePagesFormsAndRedirects() throws Exception {
        addShoesToCart();

        mvc.perform(get(shop + "/cart"))
                .andExpect(content().string(containsString(shoes.name())))
                .andExpect(content().string(containsString("Check out")));

        mvc.perform(post(shop + "/cart/checkout").param("version", "0"))
                .andExpect(redirectedUrl(shop + "/orders/" + orderTail));

        var payNow = "/customers/%s/orders/%s/payments/%s/checkout-session".formatted(customerId, orderTail, orderTail);
        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(content().string(containsString("Pay now")))
                .andExpect(content().string(containsString("action=\"%s\"".formatted(payNow))));

        var checkoutPage = mvc.perform(post(payNow))
                .andExpect(header().string("Location", startsWith("/sandbox/checkout/")))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        mvc.perform(post(checkoutPage).param("cardNumber", "4242424242424242")).andExpect(status().isSeeOther());

        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(content().string(containsString("Completed")))
                .andExpect(content().string(containsString("Sandbox Express")));
        mvc.perform(get(shop + "/orders"))
                .andExpect(content().string(containsString("Completed")))
                .andExpect(content().string(containsString(shop + "/orders/" + orderTail)));
    }

    @Test
    public void orderPageSaysTheOrderIsBeingPlacedWhileItHasNoStatus() throws Exception {
        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your order is being placed")))
                .andExpect(content().string(containsString("http-equiv=\"refresh\" content=\"2\"")));
    }

    @Test
    public void orderPageShowsTheOrderOnceItHasAStatus() throws Exception {
        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(content().string(containsString("Your order is being placed")));

        addShoesToCart();
        mvc.perform(post(shop + "/cart/checkout").param("version", "0"));

        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Order status")))
                .andExpect(content().string(matchesPattern("(?s)(?!.*Your order is being placed).*")));
    }

    @Test
    public void orderPageOfAnotherCustomerIsNotFound() throws Exception {
        addShoesToCart();
        mvc.perform(post(shop + "/cart/checkout").param("version", "0"));
        var anotherCustomer = "/shop/customers/%s".formatted(UUID.randomUUID());

        mvc.perform(get(anotherCustomer + "/orders/" + orderTail)).andExpect(status().isNotFound());
    }

    @Test
    public void theCustomerCancelsAnOrderFromItsStatusPage() throws Exception {
        addShoesToCart();
        mvc.perform(post(shop + "/cart/checkout").param("version", "0"));

        mvc.perform(post(shop + "/orders/" + orderTail + "/cancel"))
                .andExpect(redirectedUrl(shop + "/orders/" + orderTail));

        mvc.perform(get(shop + "/orders/" + orderTail))
                .andExpect(content().string(containsString("Canceled")))
                .andExpect(content().string(matchesPattern("(?s)(?!.*Pay now).*")));
    }

    @Test
    public void removingTheLastProductEmptiesTheCart() throws Exception {
        addShoesToCart();

        mvc.perform(post(shop + "/cart/items/" + shoes.id() + "/remove")
                        .param("version", "0")
                        .param("quantity", "1"))
                .andExpect(redirectedUrl(shop + "/cart"));

        mvc.perform(get(shop + "/cart")).andExpect(content().string(containsString("Your cart is empty")));
    }

    private String logIn(String name) throws Exception {
        return mvc.perform(post("/shop/login").param("name", name))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private void addShoesToCart() throws Exception {
        mvc.perform(post(shop + "/cart/items")
                        .param("productId", shoes.id().toString())
                        .param("quantity", "1"))
                .andExpect(redirectedUrl(shop + "/cart"));
    }
}
