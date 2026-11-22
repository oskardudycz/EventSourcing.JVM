package io.eventdriven.testing.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.eventdriven.testing.api.ApiE2ESpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * {@link ApiE2ESpecification} with a browser: the steps can be what a person does on the pages, in Chromium with
 * Playwright, and the expectations can check the page next to the messages, events, traces and metrics. Each browser
 * step takes part in the scenario's trace and marks the page when it ends, so {@link #expectUpdatedInPlace()} can tell an
 * update in place from a reload. A test fails when the browser reports an error.
 */
public abstract class BrowserE2ESpecification extends ApiE2ESpecification {
    private static final String STAYED_ON_PAGE = "window.stayedOnPage";
    protected static final Path SCREENSHOTS = Path.of(System.getProperty("screenshots.dir", "build/screenshots"));

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    protected int port;

    protected Page page;
    private final List<String> browserErrors = new CopyOnWriteArrayList<>();
    private final List<String> failedResponses = new CopyOnWriteArrayList<>();

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
        page = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900))
                .newPage();
        page.onConsoleMessage(message -> {
            if (message.type().equals("error")) browserErrors.add(message.text());
        });
        page.onPageError(browserErrors::add);
        page.onResponse(response -> {
            if (response.status() >= 400 && response.url().startsWith(url("")))
                failedResponses.add(response.status() + " " + response.url());
        });
    }

    @AfterEach
    void closePage() {
        page.context().close();
        assertThat(browserErrors).as("Errors the browser reported").isEmpty();
    }

    public String url(String path) {
        return "http://localhost:%d%s".formatted(port, path);
    }

    /** What a person does in the browser. The requests it makes take part in the scenario's trace. */
    public TestStep inBrowser(Consumer<Page> action) {
        return traced(traceparent -> {
            page.setExtraHTTPHeaders(Map.of("traceparent", traceparent));
            try {
                action.accept(page);
                page.evaluate(STAYED_ON_PAGE + " = true");
            } finally {
                page.setExtraHTTPHeaders(Map.of());
            }
        });
    }

    /** What another person does at the same time, in a tab of their own, so the page of this test stays as it is. */
    public TestStep inAnotherTab(Consumer<Page> action) {
        return traced(traceparent -> {
            var tab = page.context().newPage();
            try {
                tab.setExtraHTTPHeaders(Map.of("traceparent", traceparent));
                action.accept(tab);
            } finally {
                tab.close();
            }
        });
    }

    public void screenshotOf(Page tab, String name) {
        tab.screenshot(new Page.ScreenshotOptions()
                .setPath(SCREENSHOTS.resolve(name + ".png"))
                .setFullPage(true));
    }

    /** Waits until the page shows the text, as a person waits for the page to update. */
    public TestStep untilThePageShows(String text) {
        return (TestAction) () -> assertThat(page.getByText(text).first()).isVisible();
    }

    public TestStep opens(String path) {
        return inBrowser(current -> current.navigate(url(path)));
    }

    /** Saves a screenshot of the whole page, to {@code build/screenshots} or the {@code screenshots.dir} folder. */
    public TestStep screenshot(String name) {
        return (TestAction) () -> screenshotOf(page, name);
    }

    public Expectation expectScreenshot(String name) {
        return outcome -> ((TestAction) screenshot(name)).run();
    }

    public Expectation expectPage(Consumer<Page> check) {
        return outcome -> check.accept(page);
    }

    public Expectation expectPageShowing(String... texts) {
        return expectPage(current -> {
            for (var text : texts) assertThat(current.getByText(text).first()).isVisible();
        });
    }

    public Expectation expectPageNotShowing(String... texts) {
        return expectPage(current -> {
            for (var text : texts) assertThat(current.getByText(text)).hasCount(0);
        });
    }

    public Expectation expectLinks(String... names) {
        return expectPage(current -> {
            for (var name : names) assertThat(linkNamed(current, name)).isVisible();
        });
    }

    public Expectation expectLink(String name, String href) {
        return expectPage(current -> assertThat(linkNamed(current, name)).hasAttribute("href", href));
    }

    private static Locator linkNamed(Page page, String name) {
        return page.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Expectation expectPageAt(String pathPattern) {
        return expectPage(current -> assertThat(current).hasURL(Pattern.compile(".*" + pathPattern)));
    }

    public Expectation expectNoFailedResponses() {
        return outcome -> assertThat(failedResponses)
                .as("Responses of the application with an error status")
                .isEmpty();
    }

    /** The page was not reloaded since the browser step ended, so whatever changed on it changed in place. */
    public Expectation expectUpdatedInPlace() {
        return outcome -> assertThat(page.evaluate(STAYED_ON_PAGE + " === true"))
                .as("The page was not reloaded")
                .isEqualTo(true);
    }
}
