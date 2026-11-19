package io.eventdriven.introductiontoeventsourcing.e07_application_logic.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.eventstores.testing.tools.TestDatabases;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
public abstract class ApiSpecification {
    protected String apiPrefix;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("kurrentdb.connectionstring", () -> TestDatabases.kurrentDBConnectionString());
        registry.add("mongodb.connectionstring", () -> TestDatabases.mongoDBConnectionString("test"));
        registry.add("spring.datasource.url", () -> TestDatabases.postgreSQL().connectionString());
        registry.add(
                "spring.datasource.username", () -> TestDatabases.postgreSQL().userName());
        registry.add(
                "spring.datasource.password", () -> TestDatabases.postgreSQL().password());
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected RestTestClient restTestClient;

    public ApiSpecification(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getApiUrl() {
        return "http://localhost:%s/%s".formatted(port, apiPrefix);
    }

    public ApiSpecificationBuilder given(Supplier<Object> define) {
        return new ApiSpecificationBuilder(this, define);
    }

    ///////////////////
    ////   WHEN    ////
    ///////////////////

    public <T> BiFunction<RestTestClient, Object, EntityExchangeResult<T>> GET(Class<T> entityClass) {
        return GET("", entityClass);
    }

    public <T> BiFunction<RestTestClient, Object, EntityExchangeResult<T>> GET(String urlSuffix, Class<T> entityClass) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return (api, request) -> exchange(
                HttpMethod.GET,
                getApiUrl() + urlSuffix + (request != "" ? "/%s".formatted(request) : request),
                HttpEntity.EMPTY,
                entityClass);
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> POST = POST("");

    public BiFunction<RestTestClient, Object, EntityExchangeResult> POST(String urlSuffix) {
        return (api, request) -> exchange(
                HttpMethod.POST,
                getApiUrl() + urlSuffix,
                request instanceof HttpEntity<?> entity ? entity : new HttpEntity<>(request),
                Void.class);
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> PUT() {
        return PUT("");
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> PUT(String urlSuffix) {
        return PUT(urlSuffix, true);
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> PUT(String urlSuffix, boolean withEmptyBody) {
        return (api, request) -> exchange(
                HttpMethod.PUT,
                getApiUrl() + urlSuffix + (withEmptyBody ? "/%s".formatted(request) : ""),
                new HttpEntity<>(!withEmptyBody ? request : null),
                Void.class);
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> DELETE() {
        return DELETE("");
    }

    public BiFunction<RestTestClient, Object, EntityExchangeResult> DELETE(String urlSuffix) {
        return (api, request) -> exchange(
                HttpMethod.DELETE,
                getApiUrl() + urlSuffix + (request != "" ? "/%s".formatted(request) : request),
                HttpEntity.EMPTY,
                Void.class);
    }

    private <T> EntityExchangeResult<T> exchange(
            HttpMethod method, String url, HttpEntity<?> request, Class<T> responseType) {
        var requestSpec =
                this.restTestClient.method(method).uri(url).headers(headers -> headers.addAll(request.getHeaders()));

        return (request.hasBody() ? requestSpec.body(request.getBody()) : requestSpec)
                .exchange()
                .returnResult(responseType);
    }

    HttpHeaders getHeaders(Consumer<HttpHeaders> consumer) {
        var headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        consumer.accept(headers);

        return headers;
    }

    ///////////////////
    ////   THEN    ////
    ///////////////////

    public Consumer<EntityExchangeResult> OK = assertResponseStatus(HttpStatus.OK);

    public Consumer<EntityExchangeResult> CREATED = response -> {
        assertResponseStatus(HttpStatus.CREATED).accept(response);

        var locationHeader = response.getResponseHeaders().getLocation();

        assertThat(locationHeader).isNotNull();

        var location = locationHeader.toString();

        assertThat(location.startsWith(apiPrefix)).isTrue();
        assertThatCode(() -> UUID.fromString(location.substring(apiPrefix.length() + 1)))
                .doesNotThrowAnyException();
    };

    public Consumer<EntityExchangeResult> BAD_REQUEST = assertResponseStatus(HttpStatus.BAD_REQUEST);

    public Consumer<EntityExchangeResult> NOT_FOUND = assertResponseStatus(HttpStatus.NOT_FOUND);

    public Consumer<EntityExchangeResult> CONFLICT = assertResponseStatus(HttpStatus.CONFLICT);

    public Consumer<EntityExchangeResult> PRECONDITION_FAILED = assertResponseStatus(HttpStatus.PRECONDITION_FAILED);

    public Consumer<EntityExchangeResult> METHOD_NOT_ALLOWED = assertResponseStatus(HttpStatus.METHOD_NOT_ALLOWED);

    private Consumer<EntityExchangeResult> assertResponseStatus(HttpStatus status) {
        return (response) -> assertThat(response.getStatus()).isEqualTo(status);
    }

    /////////////////////
    ////   BUILDER   ////
    /////////////////////

    protected class ApiSpecificationBuilder {
        private final ApiSpecification api;
        private final Supplier<Object> given;
        private BiFunction<RestTestClient, Object, EntityExchangeResult> when;
        private EntityExchangeResult response;

        private ApiSpecificationBuilder(ApiSpecification api, Supplier<Object> given) {
            this.api = api;
            this.given = given;
        }

        public ApiSpecificationBuilder when(BiFunction<RestTestClient, Object, EntityExchangeResult> when) {
            this.when = when;

            return this;
        }

        public ApiSpecificationBuilder then(Consumer<EntityExchangeResult> then) {
            var request = given.get();

            var response = when.apply(api.restTestClient, request);

            then.accept(response);

            return this;
        }
    }
}
