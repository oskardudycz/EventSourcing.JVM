package io.eventdriven.distributedprocesses.ecommerce.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ShopInBrowserTests {
    private static final Path screenshots = Path.of(System.getProperty("screenshots.dir", "build/screenshots"));

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    private Page page;
    private final List<String> browserErrors = new ArrayList<>();

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        page = browser.newContext(new Browser.NewContextOptions().setViewportSize(1100, 800))
                .newPage();
        page.onConsoleMessage(message -> {
            if (message.type().equals("error")) browserErrors.add(message.text());
        });
        page.onPageError(browserErrors::add);
    }

    @AfterEach
    void closePage() {
        page.context().close();
        assertThat(browserErrors).as("browser errors").isEmpty();
    }

    @Test
    public void aCustomerLogsInBuysTwoProductsAndPaysOnTheSandboxPage() {
        page.navigate(url("/shop"));
        page.getByLabel("Your name").fill("Marty McFly");
        screenshot("1-login");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in"))
                .click();

        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Products")))
                .isVisible();
        assertThat(page.getByText("Marty McFly")).isVisible();
        screenshot("2-products");

        var products = page.url();
        addToCart(0);
        page.navigate(products);
        addToCart(1);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Cart")))
                .isVisible();
        screenshot("3-cart");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Check out"))
                .click();
        assertThat(page.getByText("Awaiting payment")).isVisible();
        screenshot("4-order-awaiting-payment");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay now"))
                .click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Pay with card")))
                .isVisible();
        page.locator("#cardNumber").fill("4242 4242 4242 4242");
        screenshot("5-sandbox-checkout");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Pay \\$")))
                .click();
        assertThat(page.getByText("Completed")).isVisible();
        assertThat(page.getByText("Sandbox Express")).isVisible();
        screenshot("6-order-completed");

        page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Orders").setExact(true))
                .click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Orders")))
                .isVisible();
        screenshot("7-orders");
    }

    @Test
    public void swaggerUiShowsTheWebApiOfEachModule() {
        page.navigate(url("/swagger-ui/index.html"));

        assertThat(page.getByText("/customers/{customerId}/orders", new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        screenshot("8-swagger-ui");
    }

    @Test
    public void springwolfUiShowsTheChannelsAndCanPublish() {
        page.navigate(url("/springwolf/asyncapi-ui.html"));

        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Channels")))
                .isVisible();
        assertThat(page.getByText("Auto-generated description")).hasCount(0);
        assertThat(page.getByText("No binding defined")).hasCount(0);
        screenshot("9-springwolf-ui");
    }

    private void addToCart(int product) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add to cart"))
                .nth(product)
                .click();
        page.waitForURL(Pattern.compile(".*/cart$"));
    }

    private void screenshot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshots.resolve(name + ".png"))
                .setFullPage(true));
    }

    private String url(String path) {
        return "http://localhost:%d%s".formatted(port, path);
    }
}
