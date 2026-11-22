package io.eventdriven.ecommerce.shop;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import io.eventdriven.testing.browser.BrowserE2ESpecification;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** What a customer does in the shop, as steps of a {@link BrowserE2ESpecification}. */
public class Customer {
    private final BrowserE2ESpecification shop;
    public final String name;
    public final UUID customerId;
    public final String orderTail;

    public Customer(BrowserE2ESpecification shop) {
        this(shop, "Marty McFly " + UUID.randomUUID());
    }

    public Customer(BrowserE2ESpecification shop, String name) {
        this.shop = shop;
        this.name = name;
        this.customerId =
                UUID.nameUUIDFromBytes(name.strip().toLowerCase(Locale.ROOT).getBytes(UTF_8));
        this.orderTail = "%s-1".formatted(customerId);
    }

    public String order() {
        return "/shop/customers/%s/orders/%s".formatted(customerId, orderTail);
    }

    public TestStep logsIn() {
        return shop.inBrowser(page -> {
            typesTheirName(page);
            pressesLogIn(page);
        });
    }

    public TestStep typesTheirName() {
        return shop.inBrowser(this::typesTheirName);
    }

    public TestStep pressesLogIn() {
        return shop.inBrowser(Customer::pressesLogIn);
    }

    private void typesTheirName(Page page) {
        page.navigate(shop.url("/shop"));
        page.getByLabel("Your name").fill(name);
    }

    private static void pressesLogIn(Page page) {
        button(page, "Log in").click();
        page.waitForURL(Pattern.compile(".*/products$"));
    }

    public TestStep addsToCart(int product) {
        return shop.inBrowser(page -> {
            page.navigate(shop.url("/shop/customers/%s/products".formatted(customerId)));
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add to cart"))
                    .nth(product)
                    .click();
            page.waitForURL(Pattern.compile(".*/cart$"));
        });
    }

    public TestStep checksOut() {
        return shop.inBrowser(page -> {
            button(page, "Check out").click();
            page.waitForURL(Pattern.compile(".*/orders/.*"));
        });
    }

    public TestStep paysNow() {
        return shop.inBrowser(page -> {
            button(page, "Pay now").click();
            page.waitForURL(Pattern.compile(".*/sandbox/checkout/.*"));
        });
    }

    public TestStep paysWithACardThatWorks() {
        return shop.inBrowser(page -> {
            button(page, "Pay with a card that works").click();
            page.waitForURL(Pattern.compile(".*" + Pattern.quote(order()) + "$"));
        });
    }

    public TestStep typesTheTestCard() {
        return shop.inBrowser(page -> page.locator("#cardNumber").fill("4242 4242 4242 4242"));
    }

    public TestStep pays() {
        return shop.inBrowser(page -> {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Pay \\$")))
                    .click();
            page.waitForURL(Pattern.compile(".*" + Pattern.quote(order()) + "$"));
        });
    }

    public TestStep triesADeclinedCard() {
        return shop.inBrowser(
                page -> button(page, "Try a card that is declined").click());
    }

    public TestStep opensOrders() {
        return shop.inBrowser(page -> page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Orders").setExact(true))
                .click());
    }

    public TestStep logsOut() {
        return shop.inBrowser(page -> button(page, "Log out").click());
    }

    private static com.microsoft.playwright.Locator button(Page page, String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }
}
