package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.http.ETag;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@AutoConfigureRestTestClient
public abstract class ApiSpecification {
  protected String apiPrefix;

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
    return (api, request) -> exchange(
      HttpMethod.GET,
      getApiUrl() + urlSuffix + (request != "" ? "/%s".formatted(request) : request),
      new HttpEntity<>(null, getHeaders(headers -> {})),
      entityClass
    );
  }

  public BiFunction<RestTestClient, Object, EntityExchangeResult<Void>> POST = POST("");

  public BiFunction<RestTestClient, Object, EntityExchangeResult<Void>> POST(String urlSuffix) {
    return (api, request) -> exchange(
      HttpMethod.POST,
      getApiUrl() + urlSuffix,
      request instanceof HttpEntity<?> entity ? entity : new HttpEntity<>(request, getHeaders(headers -> {})),
      Void.class
    );
  }

  public BiFunction<RestTestClient, Object, EntityExchangeResult<Void>> POST(String urlSuffix, ETag eTag) {
    return (api, request) -> exchange(
      HttpMethod.POST,
      getApiUrl() + urlSuffix,
      new HttpEntity<>(request, getIfMatchHeader(eTag)),
      Void.class
    );
  }

  public BiFunction<RestTestClient, Object, EntityExchangeResult<Void>> DELETE(String urlSuffix, @Nullable ETag eTag) {
    return (api, request) -> exchange(
      HttpMethod.DELETE,
      getApiUrl() + urlSuffix + (request != "" ? "?%s".formatted(request) : request),
      new HttpEntity<>(null, eTag != null ? getIfMatchHeader(eTag) : getHeaders(headers -> {})),
      Void.class
    );
  }

  private <T> EntityExchangeResult<T> exchange(HttpMethod method, String url, HttpEntity<?> request, Class<T> responseType) {
    var requestSpec = this.restTestClient
      .method(method)
      .uri(url)
      .headers(headers -> headers.addAll(request.getHeaders()));

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

  HttpHeaders getIfMatchHeader(ETag eTag) {
    return getHeaders(headers -> headers.setIfMatch(eTag.value()));
  }

  ///////////////////
  ////   THEN    ////
  ///////////////////

  public Consumer<EntityExchangeResult<?>> OK = assertResponseStatus(HttpStatus.OK);

  public Consumer<EntityExchangeResult<?>> ACCEPTED = assertResponseStatus(HttpStatus.ACCEPTED);

  public Consumer<EntityExchangeResult<?>> SEE_OTHER = assertResponseStatus(HttpStatus.SEE_OTHER);

  public Consumer<EntityExchangeResult<?>> BAD_REQUEST = assertResponseStatus(HttpStatus.BAD_REQUEST);

  public Consumer<EntityExchangeResult<?>> NOT_FOUND = assertResponseStatus(HttpStatus.NOT_FOUND);

  public Consumer<EntityExchangeResult<?>> CONFLICT = assertResponseStatus(HttpStatus.CONFLICT);

  public Consumer<EntityExchangeResult<?>> PRECONDITION_FAILED = assertResponseStatus(HttpStatus.PRECONDITION_FAILED);

  public Consumer<EntityExchangeResult<?>> BAD_GATEWAY = assertResponseStatus(HttpStatus.BAD_GATEWAY);

  private Consumer<EntityExchangeResult<?>> assertResponseStatus(HttpStatus status) {
    return (response) -> assertEquals(status, response.getStatus());
  }

  /////////////////////
  ////   BUILDER   ////
  /////////////////////

  protected class ApiSpecificationBuilder {
    private final ApiSpecification api;
    private final Supplier<Object> given;
    private BiFunction<RestTestClient, Object, ? extends EntityExchangeResult<?>> when;

    private ApiSpecificationBuilder(ApiSpecification api, Supplier<Object> given) {
      this.api = api;
      this.given = given;
    }

    public ApiSpecificationBuilder when(BiFunction<RestTestClient, Object, ? extends EntityExchangeResult<?>> when) {
      this.when = when;

      return this;
    }

    public ApiSpecificationBuilder then(Consumer<EntityExchangeResult<?>> then) {
      var request = given.get();

      var response = when.apply(api.restTestClient, request);

      then.accept(response);

      return this;
    }
  }
}
