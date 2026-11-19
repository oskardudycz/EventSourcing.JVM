package io.eventdriven.introductiontoeventsourcing.e07_application_logic.kurrentdb.mutable.tests.api.builders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.kurrentdb.mutable.app.api.ShoppingCartsRequests.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.http.*;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

public class ShoppingCartRestBuilder {
    private final String apiPrefix = "api/shopping-carts";
    private final RestTestClient restTestClient;
    private final int port;
    private UUID clientId;
    private boolean isConfirmed;
    private boolean isCanceled;
    private final List<ProductItemRequest> products = new ArrayList<>();

    public String getApiUrl() {
        return "http://localhost:%s/%s".formatted(port, apiPrefix);
    }

    private ShoppingCartRestBuilder(RestTestClient restTestClient, int port) {
        this.restTestClient = restTestClient;
        this.port = port;
    }

    public static ShoppingCartRestBuilder of(RestTestClient restTestClient, int port) {
        return new ShoppingCartRestBuilder(restTestClient, port);
    }

    public ShoppingCartRestBuilder withClientId(UUID clientId) {
        this.clientId = clientId;

        return this;
    }

    public ShoppingCartRestBuilder withProduct(ProductItemRequest product) {
        this.products.add(product);

        return this;
    }

    public ShoppingCartRestBuilder confirmed() {
        this.isConfirmed = true;

        return this;
    }

    public ShoppingCartRestBuilder canceled() {
        this.isCanceled = true;

        return this;
    }

    public BuilderResult build(Consumer<ShoppingCartRestBuilder> with) {
        with.accept(this);

        return execute();
    }

    public BuilderResult execute() {
        BuilderResult result = open();

        if (isConfirmed) {
            result = confirm(result);
        }

        if (isCanceled) {
            result = cancel(result);
        }

        for (var product : products) {
            result = addProduct(product, result);
        }

        return result;
    }

    private BuilderResult open() {
        var response = this.restTestClient
                .post()
                .uri(getApiUrl())
                .body(new Open(clientId))
                .exchange()
                .returnResult(Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);

        var locationHeader = response.getResponseHeaders().getLocation();

        assertThat(locationHeader).isNotNull();

        var location = locationHeader.toString();

        assertThat(location.startsWith(apiPrefix)).isTrue();
        var newId = UUID.fromString(location.substring(apiPrefix.length() + 1));

        return getResult(response, newId);
    }

    private BuilderResult addProduct(ProductItemRequest product, BuilderResult result) {
        var response = this.restTestClient
                .post()
                .uri(getApiUrl() + "/%s/products".formatted(result.id()))
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(new AddProduct(product))
                .exchange()
                .returnResult(Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        return getResult(response, result.id());
    }

    private BuilderResult confirm(BuilderResult result) {
        var response = this.restTestClient
                .put()
                .uri("%s/%s".formatted(getApiUrl(), result.id()))
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .exchange()
                .returnResult(Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        return getResult(response, result.id());
    }

    private BuilderResult cancel(BuilderResult result) {
        var response = this.restTestClient
                .delete()
                .uri("%s/%s".formatted(getApiUrl(), result.id()))
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .exchange()
                .returnResult(Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        return getResult(response, result.id());
    }

    private BuilderResult getResult(EntityExchangeResult<Void> response, UUID newId) {
        return new BuilderResult(newId);
    }

    public record BuilderResult(UUID id) {}
}
