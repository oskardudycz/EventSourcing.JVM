package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.*;
import static org.springframework.http.HttpStatus.*;

import io.eventdriven.ecommerce.OutsideWorld;
import io.eventdriven.ecommerce.Time;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent.ShoppingCartCheckedOut;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ChangeablePrices;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.api.ApiE2ESpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("in-memory")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OutsideWorld.class)
public class ShoppingCartsApiTests extends ApiE2ESpecification {
    @Autowired
    private ChangeablePrices prices;

    @Autowired
    private Time time;

    private final UUID customerId = UUID.randomUUID();
    private final UUID shoes = UUID.randomUUID();
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(customerId, 1);

    private TestStep[] cartWithTwoShoes() {
        return new TestStep[] {prices.priceOf(shoes, 50), addProductItem(customerId, shoes, 2)};
    }

    private TestStep[] checkedOutCart() {
        return new TestStep[] {
            prices.priceOf(shoes, 50), addProductItem(customerId, shoes, 2), checkOut(customerId, weakETag(0))
        };
    }

    private TestStep[] cartWhoseCheckoutFoundAChangedPrice() {
        return new TestStep[] {
            prices.priceOf(shoes, 50),
            addProductItem(customerId, shoes, 2),
            prices.priceOf(shoes, 60),
            attempt(checkOut(customerId, weakETag(0)))
        };
    }

    private TestStep[] cartWithAChangedPrice() {
        return new TestStep[] {
            prices.priceOf(shoes, 50), addProductItem(customerId, shoes, 2), prices.priceOf(shoes, 60)
        };
    }

    @Nested
    public class AddingProductItem {
        @Test
        public void addProductItem_succeeds_withFirstVersion_forCustomerWithoutOpenCart() {
            given().when(addProductItem(customerId, shoes, 2))
                    .then(expectResponse(OK, headers(Map.of(HttpHeaders.ETAG, weakETag(0)))));
        }

        @Test
        public void addProductItem_succeeds_withNextVersion_forOpenCart() {
            given(cartWithTwoShoes())
                    .when(addProductItem(customerId, shoes, 1))
                    .then(expectResponse(OK, headers(Map.of(HttpHeaders.ETAG, weakETag(1)))));
        }

        @Test
        public void addProductItem_succeeds_byStartingANewCart_afterCheckout() {
            given(checkedOutCart())
                    .when(addProductItem(customerId, shoes, 1))
                    .then(expectResponse(OK, headers(Map.of(HttpHeaders.ETAG, weakETag(0)))));
        }

        @Test
        public void addProductItem_fails_withBadRequest_forZeroQuantity() {
            given().when(addProductItem(customerId, shoes, 0)).then(expectError(BAD_REQUEST));
        }

        @Test
        public void addProductItem_fails_withBadRequest_forMissingProductId() {
            given().when(addProductItem(customerId, Map.of("quantity", 2))).then(expectError(BAD_REQUEST));
        }
    }

    @Nested
    public class RemovingProductItem {
        @Test
        public void removeProductItem_succeeds_withNextVersion_forSomeOfTheQuantity() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, shoes, 1, weakETag(0)))
                    .then(expectResponse(OK, headers(Map.of(HttpHeaders.ETAG, weakETag(1)))));
        }

        @Test
        public void removeProductItem_succeeds_forAllOfTheQuantity() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, shoes, 2, weakETag(0)))
                    .then(expectResponse(OK));
        }

        @Test
        public void removeProductItem_fails_withNotFound_forCustomerWithoutOpenCart() {
            given().when(removeProductItem(customerId, shoes, 1, weakETag(0))).then(expectError(NOT_FOUND));
        }

        @Test
        public void removeProductItem_fails_withConflict_forProductNotInCart() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, UUID.randomUUID(), 1, weakETag(0)))
                    .then(expectError(CONFLICT));
        }

        @Test
        public void removeProductItem_fails_withConflict_forMoreThanInCart() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, shoes, 3, weakETag(0)))
                    .then(expectError(CONFLICT));
        }

        @Test
        public void removeProductItem_fails_withPreconditionFailed_forWrongETag() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, shoes, 1, weakETag(999)))
                    .then(expectError(PRECONDITION_FAILED));
        }

        @Test
        public void removeProductItem_fails_withBadRequest_forMissingIfMatch() {
            given(cartWithTwoShoes())
                    .when(removeProductItem(customerId, shoes, 1))
                    .then(expectError(BAD_REQUEST));
        }
    }

    @Nested
    public class CheckingOut {
        @Test
        public void checkOut_succeeds_withAcceptedAndTheOrderStatusAsLocation_andPublishesTheCheckedOutCart() {
            given(cartWithTwoShoes())
                    .when(checkOut(customerId, weakETag(0)))
                    .then(
                            expectResponse(
                                    ACCEPTED,
                                    headers(Map.of(
                                            HttpHeaders.LOCATION,
                                            "/customers/%s/orders/%s-1".formatted(customerId, customerId)))),
                            expectPublished(new ShoppingCartCheckedOut(
                                    cartId,
                                    customerId,
                                    new PricedProductItem[] {new PricedProductItem(new ProductItem(shoes, 2), 50)},
                                    100,
                                    time.now())));
        }

        @Test
        public void checkOut_fails_withConflictAndTheNewVersion_forChangedPrice() {
            given(cartWithAChangedPrice())
                    .when(checkOut(customerId, weakETag(0)))
                    .then(
                            expectResponse(CONFLICT, headers(Map.of(HttpHeaders.ETAG, weakETag(1)))),
                            expectNothingPublished());
        }

        @Test
        public void checkOut_fails_withNotFound_forCustomerWithoutOpenCart() {
            given().when(checkOut(customerId, weakETag(0))).then(expectError(NOT_FOUND));
        }

        @Test
        public void checkOut_fails_withNotFound_forCartAlreadyCheckedOut() {
            given(checkedOutCart()).when(checkOut(customerId, weakETag(1))).then(expectError(NOT_FOUND));
        }

        @Test
        public void checkOut_fails_withPreconditionFailed_forWrongETag() {
            given(cartWithTwoShoes())
                    .when(checkOut(customerId, weakETag(999)))
                    .then(expectError(PRECONDITION_FAILED), expectNothingPublished());
        }

        @Test
        public void checkOut_fails_withPreconditionFailed_forANegativeVersionInIfMatch() {
            given(cartWithTwoShoes()).when(checkOut(customerId, weakETag(-1))).then(expectError(PRECONDITION_FAILED));
        }

        @Test
        public void checkOut_fails_withBadRequest_forMissingIfMatch() {
            given(cartWithTwoShoes()).when(checkOut(customerId)).then(expectError(BAD_REQUEST));
        }

        @Test
        public void checkOut_fails_withBadRequest_forAMalformedIfMatch() {
            given(cartWithTwoShoes())
                    .when(checkOut(customerId, "\"not-a-version\""))
                    .then(expectError(BAD_REQUEST));
        }

        @Test
        public void checkOut_fails_withBadRequest_forAnEmptyIfMatch() {
            given(cartWithTwoShoes()).when(checkOut(customerId, "")).then(expectError(BAD_REQUEST));
        }
    }

    @Nested
    public class GettingCart {
        @Test
        public void getCart_succeeds_withLinesTotalAndVersion() {
            given(cartWithTwoShoes())
                    .when(getCart(customerId))
                    .then(expectResponse(
                            OK,
                            headers(Map.of(HttpHeaders.ETAG, weakETag(0))),
                            body(Map.of(
                                    "lines",
                                    List.of(Map.of("productId", shoes.toString(), "quantity", 2, "unitPrice", 50.0)),
                                    "totalPrice",
                                    100.0))));
        }

        @Test
        public void getCart_succeeds_withTheNewPrice_afterACheckoutFoundItChanged() {
            given(cartWhoseCheckoutFoundAChangedPrice())
                    .when(getCart(customerId))
                    .then(expectResponse(
                            OK, body(Map.of("lines", List.of(Map.of("unitPrice", 60.0)), "checkedOut", false))));
        }

        @Test
        public void getCart_fails_withNotFound_forCustomerWithoutOpenCart() {
            given().when(getCart(customerId)).then(expectError(NOT_FOUND));
        }

        @Test
        public void getCart_fails_withNotFound_afterCheckout() {
            given(checkedOutCart()).when(getCart(customerId)).then(expectError(NOT_FOUND));
        }
    }
}
