package io.eventdriven.ecommerce.shop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// A customer going through the shop over the JSON API.
public class Shopper {
    public final UUID customerId = UUID.randomUUID();
    public final String orderTail = "%s-1".formatted(customerId);
    public final String firstPaymentTail = orderTail;
    public final String orders = "/customers/%s/orders".formatted(customerId);
    public final String order = orders + "/" + orderTail;
    private final MockMvc mvc;

    public Shopper(MockMvc mvc) {
        this.mvc = mvc;
    }

    public Shopper checksOut() throws Exception {
        var cart = "/customers/%s/shopping-carts/current".formatted(customerId);
        mvc.perform(post(cart + "/product-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
        mvc.perform(post(cart + "/checkout").header("If-Match", "W/\"0\"")).andExpect(status().isAccepted());
        return this;
    }

    public String checkoutSession(String paymentTail) {
        return order + "/payments/" + paymentTail + "/checkout-session";
    }

    public String opensCheckout(String paymentTail) throws Exception {
        return mvc.perform(post(checkoutSession(paymentTail)))
                .andExpect(status().isSeeOther())
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    public String paysOn(String checkoutPage, String cardNumber) throws Exception {
        return mvc.perform(post(checkoutPage).param("cardNumber", cardNumber))
                .andReturn()
                .getResponse()
                .getHeader("Location");
    }

    public Shopper pays() throws Exception {
        paysOn(opensCheckout(firstPaymentTail), "4242 4242 4242 4242");
        return this;
    }
}
