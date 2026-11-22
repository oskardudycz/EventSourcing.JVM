package io.eventdriven.ecommerce.shop;

import static io.eventdriven.ecommerce.shop.ShopRequests.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.OK;

import io.eventdriven.testing.api.ApiE2ESpecification;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ShopPagesTests extends ApiE2ESpecification {
    private static final String marty = "Marty McFly";

    @Nested
    public class LoggingIn {
        @Test
        public void aVisitorWhoIsNotLoggedInIsSentToTheLogin() {
            given().when(openShop()).then(expectResponse(FOUND, redirectsTo("/shop/login")));
        }

        @Test
        public void theLoginPageAsksForTheCustomersName() {
            given().when(loginPage()).then(expectResponse(OK, htmlContaining("Your name", "Log in")));
        }

        @Test
        public void loggingInOpensTheProductsPageAndRemembersTheCustomer() {
            given().when(logIn(marty))
                    .then(expectResponse(FOUND, redirectsTo(productsOf(marty)), settingCookie("customer=")));
        }

        @Test
        public void theSameNameInAnyCaseLogsInAsTheSameCustomer() {
            given().when(logIn("  marty mcfly ")).then(expectResponse(FOUND, redirectsTo(productsOf(marty))));
        }

        @Test
        public void aLoggedInCustomerReturnsToTheirShop() {
            given().when(openShopAs(marty)).then(expectResponse(FOUND, redirectsTo(productsOf(marty))));
        }
    }

    @Nested
    public class Header {
        @Test
        public void theHeaderShowsWhoIsLoggedInAndHowToLogOut() {
            given().when(productsPageAs(marty)).then(expectResponse(OK, htmlContaining(marty, "Log out")));
        }

        @Test
        public void theHeaderLinksTheOrdersAndTheDeveloperTools() {
            given().when(productsPageAs(marty))
                    .then(expectResponse(
                            OK,
                            htmlContaining(
                                    "/shop/customers/%s/orders".formatted(customerIdOf(marty)),
                                    "Developer tools",
                                    "href=\"http://localhost:3000\"",
                                    "href=\"/swagger-ui/index.html\"",
                                    "href=\"/springwolf/asyncapi-ui.html\"",
                                    "href=\"http://localhost:3001\"",
                                    "href=\"/sandbox/warehouse\"")));
        }
    }

    @Nested
    public class LoggingOut {
        @Test
        public void loggingOutForgetsTheCustomerAndReturnsToTheLogin() {
            given().when(logOut(marty))
                    .then(expectResponse(
                            FOUND,
                            redirectsTo("/shop/login"),
                            settingCookie("customer=;", "Expires=Thu, 01 Jan 1970")));
        }
    }

    private static ResponseExpectation settingCookie(String... parts) {
        return satisfying(response -> assertThat(response.headers().get(HttpHeaders.SET_COOKIE))
                .as("Set-Cookie")
                .anySatisfy(cookie -> assertThat(cookie).contains(parts)));
    }
}
