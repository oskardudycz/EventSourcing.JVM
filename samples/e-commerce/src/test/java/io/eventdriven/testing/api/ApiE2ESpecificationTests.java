package io.eventdriven.testing.api;

import static io.eventdriven.testing.api.ApiE2ESpecification.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.*;

import io.eventdriven.testing.messaging.DirectMessageBus;
import io.eventdriven.testing.tracing.TestTracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.*;

public class ApiE2ESpecificationTests extends ApiE2ESpecification {
    private final UUID cartId = UUID.randomUUID();
    private UUID aboutCart;

    private final TestStep[] cartWithTwoProducts = {addProductItem(cartId), publish(new ProductItemAdded(cartId))};

    @BeforeEach
    public void bindToTheCartController() {
        var tracing = new TestTracing();
        var bus = new DirectMessageBus();
        messageBus = bus;
        spans = tracing.spans();
        meters = tracing.meters();
        restTestClient = RestTestClient.bindToController(
                        new CountingCartController(bus, tracing.tracer(), tracing.meters()))
                .build();
    }

    @Test
    public void preparesTheStateThroughTheGivenRequests() {
        given(addProductItem(cartId), addProductItem(cartId))
                .when(getCart(cartId))
                .then(expectResponse(OK, body(Map.of("productItemsCount", 2))));
    }

    @Test
    public void preparesTheStateThroughMessagesPublishedOnTheBus() {
        given(publish(new ProductItemAdded(cartId)))
                .when(getCart(cartId))
                .then(expectResponse(OK, body(Map.of("productItemsCount", 1))));
    }

    @Test
    public void preparesTheStateThroughANamedScenario() {
        given(cartWithTwoProducts).when(getCart(cartId)).then(expectResponse(OK, body(Map.of("productItemsCount", 2))));
    }

    @Test
    public void anAttemptedGivenRequestMayFail() {
        given(attempt(confirm(cartId, 0)), addProductItem(cartId))
                .when(getCart(cartId))
                .then(expectResponse(OK, body(Map.of("productItemsCount", 1))));
    }

    @Test
    public void continuesANamedScenarioWithMoreSteps() {
        given(cartWithTwoProducts, addProductItem(cartId))
                .when(getCart(cartId))
                .then(expectResponse(OK, body(Map.of("productItemsCount", 3))));
    }

    @Test
    public void buildsANamedScenarioFromAnother() {
        var confirmedCart = scenario(cartWithTwoProducts, confirm(cartId, 2));

        given(confirmedCart).when(getCart(cartId)).then(expectResponse(OK, body(Map.of("status", "Confirmed"))));
    }

    @Test
    public void runsTheWhenRequestWithoutGivenSteps() {
        given().when(addProductItem(cartId)).then(expectResponse(NO_CONTENT));
    }

    @Test
    public void matchesTheExpectedHeaders() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectResponse(NO_CONTENT, headers(Map.of(HttpHeaders.ETAG, weakETag(2)))));
    }

    @Test
    public void matchesOnlyTheExpectedPartOfTheBody() {
        given(addProductItem(cartId)).when(getCart(cartId)).then(expectResponse(OK, body(Map.of("status", "Opened"))));
    }

    @Test
    public void expectsAnErrorWithItsProblemDetails() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 7))
                .then(expectError(PRECONDITION_FAILED, Map.of("detail", "Expected version 7, but it is 1")));
    }

    @Test
    public void passesTheResponseToACustomCheck() {
        given(addProductItem(cartId))
                .when(getCart(cartId))
                .then(expectResponse(
                        OK,
                        satisfying(response -> assertThat(
                                        response.json().get("cartId").asString())
                                .isEqualTo(cartId.toString()))));
    }

    @Test
    public void expectsTheMessagesThatTheWhenRequestPublished() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectResponse(NO_CONTENT), expectPublished(new CartConfirmed(cartId, 1)));
    }

    @Test
    public void expectsTheMessagesThatTheWhenMessageCaused() {
        given(addProductItem(cartId))
                .when(publish(new ConfirmCart(cartId)))
                .then(expectOnlyPublished(new ConfirmCart(cartId), new CartConfirmed(cartId, 1)));
    }

    @Test
    public void expectsSomeOfThePublishedMessagesInTheirOrder() {
        given(addProductItem(cartId))
                .when(publish(new ConfirmCart(cartId)))
                .then(expectPublished(new CartConfirmed(cartId, 1)));
    }

    @Test
    public void failsWhenExpectedMessagesWerePublishedInAnotherOrder() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(publish(new ConfirmCart(cartId)))
                        .then(expectPublished(new CartConfirmed(cartId, 1), new ConfirmCart(cartId))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ConfirmCart");
    }

    @Test
    public void failsWhenMoreMessagesWerePublishedThanExpectedOnly() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(publish(new ConfirmCart(cartId)))
                        .then(expectOnlyPublished(new CartConfirmed(cartId, 1))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ConfirmCart");
    }

    @Test
    public void failsWhenSomethingWasPublishedButNothingWasExpected() {
        assertThatThrownBy(() ->
                        given(addProductItem(cartId)).when(confirm(cartId, 1)).then(expectNothingPublished()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("CartConfirmed");
    }

    @Test
    public void expectsOnlyTheMessagesThatTheScenarioIsAbout() {
        var otherCartId = UUID.randomUUID();
        aboutCart = cartId;

        given(addProductItem(cartId), addProductItem(otherCartId))
                .when(publish(new ConfirmCart(otherCartId), new ConfirmCart(cartId)))
                .then(expectOnlyPublished(new ConfirmCart(cartId), new CartConfirmed(cartId, 1)));
    }

    @Test
    public void waitsUntilTheBusCaughtUpWithMessagesPublishedAfterTheResponse() {
        given(addProductItem(cartId))
                .when(confirmLater(cartId))
                .then(expectResponse(ACCEPTED), expectPublished(new CartConfirmed(cartId, 1)));
    }

    @Test
    public void ignoresTheMessagesThatTheGivenStepsPublished() {
        given(addProductItem(cartId), confirm(cartId, 1))
                .when(getCart(cartId))
                .then(expectResponse(OK), expectNothingPublished());
    }

    @Test
    public void givesUpAtTheCatchUpTimeoutWhenTheSystemKeepsPublishing() {
        var keepEchoing = new AtomicBoolean(true);
        var bus = (DirectMessageBus) messageBus;
        bus.subscribe("echoes", Echo.class, echo -> {
            if (keepEchoing.get()) bus.publishLater(echo, Duration.ofMillis(10));
        });
        catchUpTimeout = Duration.ofSeconds(1);

        var startedAt = System.nanoTime();

        try {
            assertThatThrownBy(() -> given().when(publish(new Echo())).then(expectResponse(OK)))
                    .isNotNull();
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
        } finally {
            keepEchoing.set(false);
        }
    }

    @Test
    public void expectsTheTraceThatTheWhenStepStarted() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectTrace(span("confirm cart", span("store cart"), span("publish CartConfirmed"))));
    }

    @Test
    public void matchesAnExpectedChildAnywhereBelowItsParent() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectTrace(span("confirm cart", span("publish CartConfirmed"))));
    }

    @Test
    public void failsWhenAnExpectedSpanIsMissing() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectTrace(span("confirm cart", span("charge card")))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("charge card");
    }

    @Test
    public void failsWhenAnExpectedSpanIsNotBelowItsExpectedParent() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectTrace(span("store cart", span("publish CartConfirmed")))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("publish CartConfirmed");
    }

    @Test
    public void expectsSpansThatTheWhenStepAddedBelowASpanOfTheGivenSteps() {
        given(addProductItem(cartId), confirm(cartId, 1))
                .when(publish(new ConfirmCart(cartId)))
                .then(expectTrace(span("confirm cart", span("handle ConfirmCart"))));
    }

    @Test
    public void matchesTheAttributesOfAnExpectedSpan() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectTrace(span("confirm cart", Map.of("cart.id", cartId.toString()), span("store cart"))));
    }

    @Test
    public void failsNamingTheAttributeThatAnExpectedSpanDoesNotHave() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectTrace(span("confirm cart", Map.of("cart.id", "another-cart")))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cart.id=another-cart");
    }

    @Test
    public void expectsOneCorrelationAcrossTheTraceAndEachCauseAboveWhatItCaused() {
        given(addProductItem(cartId)).when(confirm(cartId, 1)).then(expectCorrelatedTrace());
    }

    @Test
    public void checksTheCorrelationOfEachTraceOnItsOwn() {
        var otherCartId = UUID.randomUUID();

        given(addProductItem(otherCartId), confirm(otherCartId, 1), addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectCorrelatedTrace());
    }

    @Test
    public void failsNamingTheSpanThatCarriesAnotherCorrelation() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1, "foreign-correlation"))
                        .then(expectCorrelatedTrace()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("store cart")
                .hasMessageContaining("someone-else");
    }

    @Test
    public void failsNamingTheSpanThatIsNotBelowTheHandlingOfItsCause() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1, "misplaced-cause"))
                        .then(expectCorrelatedTrace()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("audit");
    }

    @Test
    public void failsNamingTheSpanCausedByAMessageThatNobodyHandled() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1, "unknown-cause"))
                        .then(expectCorrelatedTrace()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("store cart")
                .hasMessageContaining("never-handled");
    }

    @Test
    public void failsWhenNoSpanOfTheTraceCarriesACorrelation() {
        assertThatThrownBy(() ->
                        given(addProductItem(cartId)).when(getCart(cartId)).then(expectCorrelatedTrace()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("correlation");
    }

    @Test
    public void expectsTheIncreaseOfAMetricThatTheWhenStepCaused() {
        given(addProductItem(cartId))
                .when(confirm(cartId, 1))
                .then(expectMetric("carts.confirmed", Map.of("status", "Confirmed"), 1));
    }

    @Test
    public void countsOnlyTheIncreaseThatTheWhenStepCaused() {
        given(addProductItem(cartId), confirm(cartId, 1))
                .when(publish(new ConfirmCart(cartId)))
                .then(expectMetric("carts.confirmed", Map.of("status", "Confirmed"), 1));
    }

    @Test
    public void failsNamingTheMetricWhoseIncreaseDiffers() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectMetric("carts.confirmed", Map.of("status", "Confirmed"), 2)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("carts.confirmed");
    }

    @Test
    public void ignoresTheTracesOfOtherScenarios() {
        var otherCartId = UUID.randomUUID();
        given(addProductItem(otherCartId)).when(confirm(otherCartId, 1)).then(expectResponse(NO_CONTENT));

        assertThatThrownBy(() ->
                        given(addProductItem(cartId)).when(getCart(cartId)).then(expectTrace(span("confirm cart"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("confirm cart");
    }

    @Test
    public void aGivenRequestCanRunRequestsOfItsOwn() {
        given(addProductItem(cartId), payOnTheCheckoutPage(cartId))
                .when(getCart(cartId))
                .then(expectResponse(OK, body(Map.of("status", "Paid"))));
    }

    @Test
    public void failsWhenTheStatusDiffers() {
        assertThatThrownBy(() -> given().when(getCart(cartId)).then(expectResponse(OK)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("404");
    }

    @Test
    public void failsWhenTheExpectedPartOfTheBodyDiffers() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(getCart(cartId))
                        .then(expectResponse(OK, body(Map.of("productItemsCount", 3)))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("productItemsCount");
    }

    @Test
    public void matchesFragmentsOfTheHtmlPage() {
        given(addProductItem(cartId))
                .when(getCartPage(cartId))
                .then(expectResponse(
                        OK, htmlContaining("<h1>Your cart</h1>", "1 item"), htmlNotContaining("Your cart is empty")));
    }

    @Test
    public void failsNamingTheFragmentTheHtmlPageLacks() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(getCartPage(cartId))
                        .then(expectResponse(OK, htmlContaining("2 items"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("2 items");
    }

    @Test
    public void failsNamingTheFragmentTheHtmlPageShouldNotHave() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(getCartPage(cartId))
                        .then(expectResponse(OK, htmlNotContaining("1 item"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("1 item");
    }

    @Test
    public void failsWhenAnHtmlCheckGetsAResponseThatIsNotHtml() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(getCart(cartId))
                        .then(expectResponse(OK, htmlContaining("1"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("application/json");
    }

    @Test
    public void matchesARedirectByThePathOfItsLocation() {
        given().when(backToTheCart(cartId)).then(expectResponse(FOUND, redirectsTo("/carts/%s".formatted(cartId))));
    }

    @Test
    public void failsNamingTheLocationWhenARedirectGoesElsewhere() {
        assertThatThrownBy(() -> given().when(backToTheCart(cartId)).then(expectResponse(FOUND, redirectsTo("/carts"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("http://shop.example/carts/%s;jsessionid=A1".formatted(cartId));
    }

    @Test
    public void failsWhenAnExpectedHeaderDiffers() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectResponse(NO_CONTENT, headers(Map.of(HttpHeaders.ETAG, weakETag(5))))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(HttpHeaders.ETAG);
    }

    @Test
    public void failsWhenThePublishedMessagesDiffer() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(confirm(cartId, 1))
                        .then(expectPublished(new CartConfirmed(cartId, 2))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("CartConfirmed");
    }

    @Test
    public void failsWhenAResponseIsExpectedFromAPublishedMessage() {
        assertThatThrownBy(() -> given(addProductItem(cartId))
                        .when(publish(new ConfirmCart(cartId)))
                        .then(expectResponse(OK)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no response");
    }

    @Test
    public void failsBeforeTheWhenStepWhenAGivenRequestFails() {
        assertThatThrownBy(() ->
                        given(confirm(cartId, 0)).when(addProductItem(cartId)).then(expectResponse(NO_CONTENT)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Given request")
                .hasMessageContaining("404");
    }

    @Override
    protected boolean isAbout(Object message) {
        return aboutCart == null || message.toString().contains(aboutCart.toString());
    }

    record ProductItemAdded(UUID cartId) {}

    record ConfirmCart(UUID cartId) {}

    record Echo() {}

    record CartConfirmed(UUID cartId, int productItemsCount) {}

    private static TestRequest addProductItem(UUID cartId) {
        return request -> request.post().uri("/carts/{cartId}/product-items", cartId);
    }

    private static TestRequest confirm(UUID cartId, long version) {
        return request ->
                request.post().uri("/carts/{cartId}/confirm", cartId).header(HttpHeaders.IF_MATCH, weakETag(version));
    }

    private static TestRequest confirm(UUID cartId, long version, String fault) {
        return request -> request.post()
                .uri("/carts/{cartId}/confirm?fault={fault}", cartId, fault)
                .header(HttpHeaders.IF_MATCH, weakETag(version));
    }

    private static TestRequest confirmLater(UUID cartId) {
        return request -> request.post().uri("/carts/{cartId}/confirm-later", cartId);
    }

    private static TestRequest getCartPage(UUID cartId) {
        return request -> request.get().uri("/carts/{cartId}/page", cartId);
    }

    private static TestRequest backToTheCart(UUID cartId) {
        return request -> request.post().uri("/carts/{cartId}/back", cartId);
    }

    private static TestRequest getCart(UUID cartId) {
        return request -> request.get().uri("/carts/{cartId}", cartId);
    }

    private static TestRequest payOnTheCheckoutPage(UUID cartId) {
        return request -> {
            var checkoutPage = request.post()
                    .uri("/carts/{cartId}/checkout-session", cartId)
                    .exchange()
                    .returnResult()
                    .getResponseHeaders()
                    .getLocation();

            return request.post().uri(checkoutPage);
        };
    }

    @RestController
    @RequestMapping("/carts/{cartId}")
    static class CountingCartController {
        private record Cart(UUID cartId, int productItemsCount, String status) {}

        private final Map<UUID, Cart> carts = new ConcurrentHashMap<>();
        private final Map<UUID, TraceContext> confirmations = new ConcurrentHashMap<>();
        private final DirectMessageBus messageBus;
        private final Tracer tracer;
        private final MeterRegistry meters;

        CountingCartController(DirectMessageBus messageBus, Tracer tracer, MeterRegistry meters) {
            this.messageBus = messageBus;
            this.tracer = tracer;
            this.meters = meters;
            messageBus.subscribe("carts", ProductItemAdded.class, added -> add(added.cartId()));
            messageBus.subscribe("carts", ConfirmCart.class, confirm -> {
                var confirmation = confirmations.get(confirm.cartId());
                var span = (confirmation != null ? tracer.spanBuilder().setParent(confirmation) : tracer.spanBuilder())
                        .name("handle ConfirmCart")
                        .start();
                try (var _ = tracer.withSpan(span)) {
                    confirmCart(confirm.cartId(), "correlation-" + confirm.cartId(), "confirm-" + confirm.cartId());
                } finally {
                    span.end();
                }
            });
        }

        @GetMapping(value = "/page", produces = MediaType.TEXT_HTML_VALUE)
        ResponseEntity<?> page(@PathVariable UUID cartId) {
            var cart = carts.get(cartId);

            if (cart == null) return ResponseEntity.ok("<h1>Your cart</h1><p>Your cart is empty</p>");

            return ResponseEntity.ok("<h1>Your cart</h1><p>%s item</p>".formatted(cart.productItemsCount()));
        }

        @PostMapping("/back")
        ResponseEntity<?> back(@PathVariable UUID cartId) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("http://shop.example/carts/%s;jsessionid=A1".formatted(cartId)))
                    .build();
        }

        @GetMapping
        ResponseEntity<?> get(@PathVariable UUID cartId) {
            var cart = carts.get(cartId);

            return cart != null ? ResponseEntity.ok(cart) : notFound(cartId);
        }

        @PostMapping("/product-items")
        ResponseEntity<?> addProductItem(@PathVariable UUID cartId) {
            var cart = add(cartId);

            return ResponseEntity.noContent()
                    .eTag(weakETag(cart.productItemsCount()))
                    .build();
        }

        @PostMapping("/confirm")
        ResponseEntity<?> confirm(
                @PathVariable UUID cartId,
                @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
                @RequestHeader(name = "traceparent", required = false) String traceparent,
                @RequestParam(defaultValue = "") String fault) {
            var cart = carts.get(cartId);

            if (cart == null) return notFound(cartId);

            if (!ifMatch.equals(weakETag(cart.productItemsCount())))
                return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                                HttpStatus.PRECONDITION_FAILED,
                                "Expected version %s, but it is %s"
                                        .formatted(ifMatch.replaceAll("\\D", ""), cart.productItemsCount())))
                        .build();

            var correlationId = "correlation-" + cartId;
            var messageId = "confirm-" + cartId;
            var span = continuing(traceparent)
                    .name("confirm cart")
                    .tag("cart.id", cartId.toString())
                    .tag("messaging.message.id", messageId)
                    .tag("messaging.message.correlation_id", correlationId)
                    .tag("messaging.message.causation_id", correlationId)
                    .start();
            confirmations.put(cartId, span.context());
            try (var _ = tracer.withSpan(span)) {
                inSpan(
                        "store cart",
                        fault.equals("foreign-correlation") ? "someone-else" : correlationId,
                        fault.equals("unknown-cause") ? "never-handled" : messageId,
                        () -> {});
                confirmCart(cartId, correlationId, messageId);
            } finally {
                span.end();
            }
            if (fault.equals("misplaced-cause")) {
                var audit = continuing(traceparent)
                        .name("audit")
                        .tag("messaging.message.correlation_id", correlationId)
                        .tag("messaging.message.causation_id", messageId)
                        .start();
                audit.end();
            }

            return ResponseEntity.noContent()
                    .eTag(weakETag(cart.productItemsCount() + 1))
                    .build();
        }

        @PostMapping("/confirm-later")
        ResponseEntity<?> confirmLater(@PathVariable UUID cartId) {
            messageBus.publishLater(new ConfirmCart(cartId), Duration.ofMillis(100));

            return ResponseEntity.accepted().build();
        }

        @PostMapping("/checkout-session")
        ResponseEntity<?> openCheckout(@PathVariable UUID cartId) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/carts/%s/checkout-page".formatted(cartId)))
                    .build();
        }

        @PostMapping("/checkout-page")
        ResponseEntity<?> pay(@PathVariable UUID cartId) {
            var cart = carts.get(cartId);

            if (cart == null) return notFound(cartId);

            carts.put(cartId, new Cart(cartId, cart.productItemsCount(), "Paid"));

            return ResponseEntity.noContent().build();
        }

        private Cart add(UUID cartId) {
            return carts.merge(
                    cartId,
                    new Cart(cartId, 1, "Opened"),
                    (current, added) -> new Cart(cartId, current.productItemsCount() + 1, current.status()));
        }

        private void confirmCart(UUID cartId, String correlationId, String causationId) {
            var cart = carts.get(cartId);
            carts.put(cartId, new Cart(cartId, cart.productItemsCount(), "Confirmed"));
            meters.counter("carts.confirmed", "status", "Confirmed").increment();
            inSpan(
                    "publish CartConfirmed",
                    correlationId,
                    causationId,
                    () -> messageBus.publish(List.of(new CartConfirmed(cartId, cart.productItemsCount()))));
        }

        private Span.Builder continuing(String traceparent) {
            if (traceparent == null) return tracer.spanBuilder();

            var parts = traceparent.split("-");
            return tracer.spanBuilder()
                    .setParent(tracer.traceContextBuilder()
                            .traceId(parts[1])
                            .spanId(parts[2])
                            .sampled(true)
                            .build());
        }

        private void inSpan(String name, String correlationId, String causationId, Runnable action) {
            var span = tracer.nextSpan()
                    .name(name)
                    .tag("messaging.message.correlation_id", correlationId)
                    .tag("messaging.message.causation_id", causationId)
                    .start();
            try (var _ = tracer.withSpan(span)) {
                action.run();
            } finally {
                span.end();
            }
        }

        private static ResponseEntity<?> notFound(UUID cartId) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                            HttpStatus.NOT_FOUND, "Cart '%s' was not found".formatted(cartId)))
                    .build();
        }
    }
}
