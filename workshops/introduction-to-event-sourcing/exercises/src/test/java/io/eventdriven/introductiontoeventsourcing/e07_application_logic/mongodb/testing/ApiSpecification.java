package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.testing;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

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
    return "http://localhost:%s/%s".formatted(port, apiPrefix);
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
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + (request != "" ? "/%s".formatted(request) : request),
        HttpMethod.GET,
        new HttpEntity<>(null),
        entityClass
      );
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST = POST("");

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST(String urlSuffix) {
    return (api, request) -> this.restTemplate
      .postForEntity(getApiUrl() + urlSuffix, request, Void.class);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT() {
    return PUT("");
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT(String urlSuffix) {
    return PUT(urlSuffix, true);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> PUT(String urlSuffix, boolean withEmptyBody) {
    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + (withEmptyBody ? "/%s".formatted(request) : ""),
        HttpMethod.PUT,
        new HttpEntity<>(!withEmptyBody ? request : null),
        Void.class
      );
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> DELETE() {
    return DELETE("");
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> DELETE(String urlSuffix) {
    return (api, request) -> this.restTemplate
      .exchange(
        getApiUrl() + urlSuffix + (request != "" ? "/%s".formatted(request) : request),
        HttpMethod.DELETE,
        new HttpEntity<>(null),
        Void.class
      );
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

  public Consumer<ResponseEntity> OK = assertResponseStatus(HttpStatus.OK);

  public Consumer<ResponseEntity> CREATED =
    response -> {
      assertResponseStatus(HttpStatus.CREATED).accept(response);

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
