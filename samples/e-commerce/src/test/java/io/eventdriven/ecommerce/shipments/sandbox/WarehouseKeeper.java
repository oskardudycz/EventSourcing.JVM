package io.eventdriven.ecommerce.shipments.sandbox;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.eventdriven.ecommerce.shop.Customer;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import io.eventdriven.testing.browser.BrowserE2ESpecification;
import java.time.Duration;

/** What a person at the warehouse does on the sandbox warehouse page, in a tab of their own. */
public class WarehouseKeeper {
    private final BrowserE2ESpecification shop;

    public WarehouseKeeper(BrowserE2ESpecification shop) {
        this.shop = shop;
    }

    public TestStep sendsThePackageOf(Customer customer) {
        return shop.inAnotherTab(tab -> press(tab, customer, "Send"));
    }

    public TestStep sendsThePackageOf(Customer customer, String screenshotBefore) {
        return shop.inAnotherTab(tab -> {
            packageOf(tab, customer).waitFor();
            shop.screenshotOf(tab, screenshotBefore);
            press(tab, customer, "Send");
        });
    }

    public TestStep rejectsThePackageOf(Customer customer) {
        return shop.inAnotherTab(tab -> press(tab, customer, "Reject"));
    }

    private void press(Page tab, Customer customer, String button) {
        packageOf(tab, customer)
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(button))
                .click();
        tab.waitForURL(url -> url.endsWith("/sandbox/warehouse"));
    }

    private Locator packageOf(Page tab, Customer customer) {
        var row = tab.locator("tr:has(td[title='%s'])".formatted(customer.orderTail));
        var giveUpAt = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        tab.navigate(shop.url("/sandbox/warehouse"));
        while (row.count() == 0 && System.nanoTime() < giveUpAt) {
            tab.waitForTimeout(250);
            tab.reload();
        }
        return row;
    }
}
