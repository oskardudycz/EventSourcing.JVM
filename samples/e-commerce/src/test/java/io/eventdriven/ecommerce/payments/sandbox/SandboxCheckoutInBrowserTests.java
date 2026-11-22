package io.eventdriven.ecommerce.payments.sandbox;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import io.eventdriven.ecommerce.shop.Customer;
import io.eventdriven.testing.browser.BrowserE2ESpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The sandbox payment page, laid out like Stripe Checkout, as a customer reaches it with "Pay now". */
@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SandboxCheckoutInBrowserTests extends BrowserE2ESpecification {
    private final Customer customer = new Customer(this);
    private final TestStep[] orderAwaitingPayment = {customer.logsIn(), customer.addsToCart(0), customer.checksOut()};

    @Test
    public void payNowOpensThePaymentPageWithTheCardFormAndTheWayBack() {
        given(orderAwaitingPayment)
                .when(customer.paysNow())
                .then(
                        expectPageAt("/sandbox/checkout/.*"),
                        expectPageShowing("Pay with card", "Back to Demo Shop"),
                        expectNoFailedResponses());
    }

    @Test
    public void theCardNumberFieldSuggestsTheTestCardsAndWhatEachDoes() {
        given(orderAwaitingPayment).when(customer.paysNow()).then(expectPage(page -> {
            var testCards = page.locator("#test-cards option");
            assertThat(testCards).hasCount(2);
            assertThat(testCards.nth(0)).hasAttribute("value", "4242 4242 4242 4242");
            assertThat(testCards.nth(1)).hasAttribute("value", "4000 0000 0000 0002");
        }));
    }

    @Test
    public void payingWithTheCardThatWorksReturnsToTheOrderWhichIsProcessing() {
        given(scenario(orderAwaitingPayment, customer.paysNow()))
                .when(customer.paysWithACardThatWorks())
                .then(expectPageAt(customer.order()), expectPageShowing("Processing"), expectNoFailedResponses());
    }

    @Test
    public void aDeclinedCardStaysOnThePageSoTheCustomerCanTryAgain() {
        given(scenario(orderAwaitingPayment, customer.paysNow()))
                .when(customer.triesADeclinedCard())
                .then(
                        expectPageAt("/sandbox/checkout/.*"),
                        expectPageShowing("Your card was declined.", "Pay with card"));
    }
}
