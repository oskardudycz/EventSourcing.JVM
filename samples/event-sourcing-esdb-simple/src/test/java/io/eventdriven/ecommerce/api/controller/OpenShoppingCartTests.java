package io.eventdriven.ecommerce.api.controller;

import io.eventdriven.ecommerce.ECommerceApplication;
import io.eventdriven.ecommerce.api.requests.ShoppingCartsRequests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ECommerceApplication.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OpenShoppingCartTests {
  private final String apiPrefix = "api/shopping-carts/";

  @LocalServerPort
  private int port;

  private UUID clientId = UUID.randomUUID();

  @Autowired
  private TestRestTemplate restTemplate;

  public String getApiUrl(){
    return "http://localhost:%s/%s".formatted(port, apiPrefix);
  }

  @Test
  public void shouldSucceedForValidData()
  {
    var request = new ShoppingCartsRequests.Open(clientId);

    var response = this.restTemplate
      .postForEntity(getApiUrl(), request, Void.class);

    assertEquals(201, response.getStatusCodeValue());

    var locationHeader = response.getHeaders().getLocation();

    assertNotNull(locationHeader);

    var location = locationHeader.toString();
    var newId = location.substring(apiPrefix.length());

    assertTrue(location.startsWith(apiPrefix));
    assertDoesNotThrow(() -> UUID.fromString(newId));
  }
}
