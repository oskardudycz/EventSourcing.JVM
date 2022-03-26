package io.eventdriven.ecommerce.testing;


import io.eventdriven.ecommerce.core.http.ETag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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

  // when
  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST =
    (api, request) -> this.restTemplate
      .postForEntity(getApiUrl(), request, Void.class);


  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST(String urlSuffix){
    return (api, request) -> this.restTemplate
      .postForEntity(getApiUrl() + urlSuffix, request, Void.class);
  }

  public BiFunction<TestRestTemplate, Object, ResponseEntity> POST(String urlSuffix, ETag eTag){
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setIfMatch(eTag.value());

    return (api, request) -> this.restTemplate
      .postForEntity(getApiUrl() + urlSuffix, new HttpEntity<>(request, headers), Void.class);
  }


  // then
  public Consumer<ResponseEntity> OK =
    (response) -> assertEquals(HttpStatus.OK, response.getStatusCode());

  public Consumer<ResponseEntity> CREATED =
    response -> {
      assertEquals(HttpStatus.CREATED, response.getStatusCode());

      var locationHeader = response.getHeaders().getLocation();

      assertNotNull(locationHeader);

      var location = locationHeader.toString();

      assertTrue(location.startsWith(apiPrefix));
      assertDoesNotThrow(() -> UUID.fromString(location.substring(apiPrefix.length())));
    };

  public Consumer<ResponseEntity> BAD_REQUEST =
    (response) -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

  public Consumer<ResponseEntity> NOT_FOUND =
    (response) -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

  public Consumer<ResponseEntity> PRECONDITION_FAILED =
    (response) -> assertEquals(HttpStatus.PRECONDITION_FAILED, response.getStatusCode());


  protected class ApiSpecificationBuilder {
    private final ApiSpecification api;
    private final Supplier<Object> given;
    private BiFunction<TestRestTemplate, Object, ResponseEntity> when;

    private ApiSpecificationBuilder(ApiSpecification api, Supplier<Object> given) {
      this.api = api;
      this.given = given;
    }

    public ApiSpecificationBuilder when(BiFunction<TestRestTemplate, Object, ResponseEntity> when) {
      this.when = when;

      return this;
    }

    public <T> ApiSpecificationBuilder then(Consumer<ResponseEntity> then) {
      var request = given.get();

      var response = when.apply(api.restTemplate, request);

      then.accept(response);

      return this;
    }
  }
}
