package io.eventdriven.ecommerce.shop;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.eventdriven.ecommerce.shipments.sandbox.WarehouseKeeper;
import io.eventdriven.testing.browser.BrowserE2ESpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The tour of the shop and the developer pages that HOWTO.md shows, with a screenshot of each stop. */
@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ShopInBrowserTests extends BrowserE2ESpecification {
    private final Customer marty = new Customer(this, "Marty McFly");
    private final WarehouseKeeper warehouse = new WarehouseKeeper(this);

    @Test
    public void aCustomerLogsInBuysTwoProductsAndPaysOnTheSandboxPage() {
        given(
                        marty.typesTheirName(),
                        screenshot("1-login"),
                        marty.pressesLogIn(),
                        screenshot("2-products"),
                        marty.addsToCart(0),
                        marty.addsToCart(1),
                        screenshot("3-cart"),
                        marty.checksOut(),
                        screenshot("4-order-awaiting-payment"),
                        marty.paysNow(),
                        marty.typesTheTestCard(),
                        screenshot("5-sandbox-checkout"),
                        marty.pays(),
                        warehouse.sendsThePackageOf(marty, "6-sandbox-warehouse"),
                        marty.waitsUntilTheOrderIs("Completed"),
                        screenshot("6-order-completed"))
                .when(marty.opensOrders())
                .then(
                        expectPageAt("/shop/customers/%s/orders".formatted(marty.customerId)),
                        expectPageShowing("Marty McFly", "Completed"),
                        expectNoFailedResponses(),
                        expectScreenshot("7-orders"));
    }

    @Test
    public void swaggerUiShowsTheWebApiOfEachModule() {
        given().when(opens("/swagger-ui/index.html"))
                .then(
                        expectPage(page -> assertThat(page.getByText(
                                        "/customers/{customerId}/orders", new Page.GetByTextOptions().setExact(true)))
                                .isVisible()),
                        expectScreenshot("8-swagger-ui"));
    }

    @Test
    public void springwolfUiShowsTheChannelsAndCanPublish() {
        given().when(opens("/springwolf/asyncapi-ui.html"))
                .then(
                        expectPage(page -> assertThat(page.getByRole(
                                        AriaRole.HEADING, new Page.GetByRoleOptions().setName("Channels")))
                                .isVisible()),
                        expectPageNotShowing("Auto-generated description", "No binding defined"),
                        expectScreenshot("9-springwolf-ui"));
    }
}
