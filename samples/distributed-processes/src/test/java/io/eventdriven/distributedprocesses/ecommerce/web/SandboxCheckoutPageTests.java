package io.eventdriven.distributedprocesses.ecommerce.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SandboxCheckoutPageTests {
  @Autowired
  private MockMvc mvc;

  @Test
  public void theCheckoutPageShowsTheAmountTheCardFormAndTheWayBack() throws Exception {
    var shopper = new Shopper(mvc).checksOut();
    var checkoutPage = shopper.opensCheckout(shopper.firstPaymentTail);

    mvc.perform(get(checkoutPage))
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("name=\"cardNumber\"")))
      .andExpect(content().string(containsString("/shop/customers/%s/orders/%s".formatted(shopper.customerId, shopper.orderTail))));
  }

  @Test
  public void payingWithTheTestCardSendsTheCustomerBackToTheOrderStatusPage() throws Exception {
    var shopper = new Shopper(mvc).checksOut();
    var checkoutPage = shopper.opensCheckout(shopper.firstPaymentTail);

    mvc.perform(post(checkoutPage).param("cardNumber", "4242 4242 4242 4242"))
      .andExpect(status().isSeeOther())
      .andExpect(header().string("Location", endsWith("/shop/customers/%s/orders/%s".formatted(shopper.customerId, shopper.orderTail))));

    mvc.perform(get(shopper.order)).andExpect(jsonPath("$.actions").isEmpty());
  }

  @Test
  public void aDeclinedCardStaysOnThePageSoTheCustomerCanTryAgain() throws Exception {
    var shopper = new Shopper(mvc).checksOut();
    var checkoutPage = shopper.opensCheckout(shopper.firstPaymentTail);

    mvc.perform(post(checkoutPage).param("cardNumber", "4000 0000 0000 0002"))
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("Your card was declined.")));

    mvc.perform(get(shopper.order))
      .andExpect(jsonPath("$.progress").value("AwaitingPayment"));

    mvc.perform(post(checkoutPage).param("cardNumber", "4242 4242 4242 4242"))
      .andExpect(status().isSeeOther());
  }

  @Test
  public void aCheckoutThatDoesNotExistIsNotFound() throws Exception {
    mvc.perform(get("/sandbox/checkout/unknown")).andExpect(status().isNotFound());
  }
}
