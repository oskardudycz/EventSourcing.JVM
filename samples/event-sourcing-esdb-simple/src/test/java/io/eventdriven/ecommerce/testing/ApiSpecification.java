package io.eventdriven.ecommerce.testing;


import io.eventdriven.ecommerce.core.http.ETag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.lang.Nullable;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ApiSpecification {
  protected String apiPrefix;

  @LocalServerPort
  protected int port;

  @Autowired
  protected TestRestTemplate restTemplate;

  public ApiSpecification(String apiPrefix) {
    this.apiPrefix = apiPrefix;
  }

  public String getApiUrl() {
    return "http://localhost:%s/%s/".formatted(port, apiPrefix);
  }

  public ApiSpecificationBuilder given(Supplier<Object> define) {
    return new ApiSpecificationBuilder(this, define);
  }

  ///////////////////
  ////   WHEN    ////
  ///////////////////

  public <T> BiFunction<TestRestTemplate, Object, ResponseEntity<T>> GET(Class<T> entityClass) {
    return GET("", entityClass);
  }

  public <T> BiFunction<TestRestTemplate, Object, ResponseEntity<T>> GET(String urlSuffix, Class<T> entityClass) {
    return GET(urlSuffix, null, entityClass);
  }

  public <T> BiFunction<TestRestTemplate, Object, ResponseEntity<T>> GET(@Nullable ETag eTag, Class<T> entityClass) {
    return GET("", eTag, entityClass);
  }

  public <T> BiFunction<TestRestTemplate, Object, ResponseEntity<T>> GET(String urlSuffix, @Nullable ETag eTag, Class<T> entityClass) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (eTag != null) {
      headers.setIfNoneMatch(eTag.value());
    }

    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + request,
        HttpMethod.GET,
        new HttpEntity<>(null, getIfNoneMatchHeader(eTag)),
        entityClass
      );
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST = POST("");

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST(String urlSuffix) {
    return (api, request) -> this.restTemplate
      .postForEntity(getApiUrl() + urlSuffix, request, Void.class);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST(String urlSuffix, ETag eTag) {
    return (api, request) -> this.restTemplate
      .postForEntity(
        getApiUrl() + urlSuffix,
        new HttpEntity<>(request, getIfMatchHeader(eTag)),
        Void.class
      );
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT(ETag eTag) {
    return PUT("", eTag);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT(String urlSuffix, ETag eTag) {
    return PUT(urlSuffix, eTag, true);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT(String urlSuffix, ETag eTag, boolean withEmptyBody) {
    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + (withEmptyBody ? request : ""),
        HttpMethod.PUT,
        new HttpEntity<>(!withEmptyBody ? request : null, getIfMatchHeader(eTag)),
        Void.class
      );
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> DELETE(ETag eTag) {
    return DELETE("", eTag);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> DELETE(String urlSuffix, ETag eTag) {
    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + request,
        HttpMethod.DELETE,
        new HttpEntity<>(null, getIfMatchHeader(eTag)),
        Void.class
      );
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

  HttpHeaders getIfNoneMatchHeader(@Nullable ETag eTag) {
    return getHeaders(headers -> {
      if (eTag != null)
        headers.setIfNoneMatch(eTag.value());
    });
  }


  ///////////////////
  ////   THEN    ////
  ///////////////////

  public Consumer<ResponseEntity> OK = assertResponseStatus(HttpStatus.OK);

  public Consumer<ResponseEntity> CREATED =
    response -> {
      assertResponseStatus(HttpStatus.CREATED);

      var locationHeader = response.getHeaders().getLocation();

      assertNotNull(locationHeader);

      var location = locationHeader.toString();

      assertTrue(location.startsWith(apiPrefix));
      assertDoesNotThrow(() -> UUID.fromString(location.substring(apiPrefix.length() + 1)));
    };

  public Consumer<ResponseEntity> BAD_REQUEST = assertResponseStatus(HttpStatus.BAD_REQUEST);

  public Consumer<ResponseEntity> NOT_FOUND = assertResponseStatus(HttpStatus.NOT_FOUND);

  public Consumer<ResponseEntity> CONFLICT = assertResponseStatus(HttpStatus.CONFLICT);

  public Consumer<ResponseEntity> PRECONDITION_FAILED = assertResponseStatus(HttpStatus.PRECONDITION_FAILED);

  public Consumer<ResponseEntity> METHOD_NOT_ALLOWED = assertResponseStatus(HttpStatus.METHOD_NOT_ALLOWED);

  private Consumer<ResponseEntity> assertResponseStatus(HttpStatus status) {
    return (response) -> assertEquals(status, response.getStatusCode());
  }


  /////////////////////
  ////   BUILDER   ////
  /////////////////////

  protected class ApiSpecificationBuilder {
    private final ApiSpecification api;
    private final Supplier<Object> given;
    private BiFunction<TestRestTemplate, Object, ResponseEntity> when;
    private ResponseEntity response;

    private ApiSpecificationBuilder(ApiSpecification api, Supplier<Object> given) {
      this.api = api;
      this.given = given;
    }

    public ApiSpecificationBuilder when(BiFunction<TestRestTemplate, Object, ResponseEntity> when) {
      this.when = when;

      return this;
    }

    public ApiSpecificationBuilder then(Consumer<ResponseEntity> then) {
      var request = given.get();

      var response = when.apply(api.restTemplate, request);

      then.accept(response);

      return this;
    }
  }
}
