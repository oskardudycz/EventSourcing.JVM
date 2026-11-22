package io.eventdriven.ecommerce.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private String logIn(String name) throws Exception {
        return mvc.perform(post("/shop/login").param("name", name))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }
}
