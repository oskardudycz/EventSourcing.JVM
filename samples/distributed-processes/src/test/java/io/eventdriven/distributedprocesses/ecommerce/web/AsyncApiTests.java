package io.eventdriven.distributedprocesses.ecommerce.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AsyncApiTests {
  @Autowired
  private MockMvc mvc;

  @Test
  public void paymentsDocumentsItsRequestsResponsesAndInternalChannels() throws Exception {
    assertThat(channelsOf("payments")).isEqualTo(Map.of(
      "payments-requests", Set.of("AuthorizePayment", "CapturePayment", "CancelPayment"),
      "payments-responses", Set.of("PaymentAuthorized", "PaymentCaptured", "PaymentFailed"),
      "payments-internal", Set.of(
        "ConfirmPaymentAuthorization", "ConfirmPaymentCapture", "FailPaymentCapture",
        "TimeOutPayment", "ExpirePaymentAuthorization"
      )
    ));
  }

  @Test
  public void shipmentsDocumentsItsRequestsResponsesAndInternalChannels() throws Exception {
    assertThat(channelsOf("shipments")).isEqualTo(Map.of(
      "shipments-requests", Set.of("ReserveStock", "RequestFulfillment", "ReleaseStock"),
      "shipments-responses", Set.of("StockReserved", "PackageWasSent", "ShipmentFailed"),
      "shipments-internal", Set.of("ConfirmPackageSent", "RejectFulfillment", "DeliverPackage")
    ));
  }

  @Test
  public void shoppingCartsDocumentsItsExternalEvents() throws Exception {
    assertThat(channelsOf("shopping_carts")).isEqualTo(Map.of(
      "shopping_carts-external", Set.of("ShoppingCartCheckedOut")
    ));
  }

  @Test
  public void ordersDocumentsItsInternalChannelAndItsOwnQueues() throws Exception {
    assertThat(channelsOf("orders")).isEqualTo(Map.of(
      "orders-internal", Set.of(
        "PlaceOrder", "RecordOrderPaymentAuthorization", "RecordOrderStockReservation",
        "RecordOrderPackageSent", "RecordOrderPaymentCapture", "RecordOrderPaymentFailure",
        "RecordOrderShipmentFailure", "CloseUnpaidOrder"
      ),
      "orders-shopping_carts-external", Set.of("ShoppingCartCheckedOut"),
      "orders-payments-responses", Set.of("PaymentAuthorized", "PaymentCaptured", "PaymentFailed"),
      "orders-shipments-responses", Set.of("StockReserved", "PackageWasSent", "ShipmentFailed"),
      "payments-requests", Set.of("AuthorizePayment", "CapturePayment", "CancelPayment"),
      "shipments-requests", Set.of("ReserveStock", "RequestFulfillment", "ReleaseStock")
    ));
  }

  @Test
  public void eachDocumentHasOnlyTheOperationsOfItsModule() throws Exception {
    assertThat(actionsOf("payments")).isEqualTo(Map.of(
      "payments-requests", Set.of("receive"),
      "payments-responses", Set.of("send"),
      "payments-internal", Set.of("receive")
    ));
    assertThat(actionsOf("shipments")).isEqualTo(Map.of(
      "shipments-requests", Set.of("receive"),
      "shipments-responses", Set.of("send"),
      "shipments-internal", Set.of("receive")
    ));
    assertThat(actionsOf("shopping_carts")).isEqualTo(Map.of(
      "shopping_carts-external", Set.of("send")
    ));
    assertThat(actionsOf("orders")).isEqualTo(Map.of(
      "orders-internal", Set.of("send", "receive"),
      "orders-shopping_carts-external", Set.of("receive"),
      "orders-payments-responses", Set.of("receive"),
      "orders-shipments-responses", Set.of("receive"),
      "payments-requests", Set.of("send"),
      "shipments-requests", Set.of("send")
    ));
  }

  @Test
  public void commandsAndEventsAreMarkedForEventCatalog() throws Exception {
    var messages = documentOf("payments").get("components").get("messages");

    assertThat(typeOf(messages, "AuthorizePayment")).isEqualTo("command");
    assertThat(typeOf(messages, "ConfirmPaymentAuthorization")).isEqualTo("command");
    assertThat(typeOf(messages, "PaymentAuthorized")).isEqualTo("event");
    assertThat(typeOf(documentOf("shopping_carts").get("components").get("messages"), "ShoppingCartCheckedOut"))
      .isEqualTo("event");
  }

  @Test
  public void theDocumentsAreAsyncApi31AndTheUiIsServed() throws Exception {
    assertThat(documentOf("payments").get("asyncapi").asString()).isEqualTo("3.1.0");
    mvc.perform(get("/springwolf/asyncapi-ui.html")).andExpect(status().isOk());
  }

  private Map<String, Set<String>> channelsOf(String group) throws Exception {
    var document = documentOf(group);
    var channels = new TreeMap<String, Set<String>>();

    for (var channel : document.get("channels").properties()) {
      var messages = new TreeSet<String>();
      for (var message : channel.getValue().get("messages").properties()) {
        var ref = message.getValue().get("$ref").asString();
        var component = document.get("components").get("messages").get(ref.substring(ref.lastIndexOf('/') + 1));
        messages.add(component.get("title").asString());
      }
      channels.put(channel.getValue().get("address").asString(), messages);
    }

    return channels;
  }

  private Map<String, Set<String>> actionsOf(String group) throws Exception {
    var document = documentOf(group);
    var actions = new TreeMap<String, Set<String>>();

    for (var operation : document.get("operations").properties()) {
      var ref = operation.getValue().get("channel").get("$ref").asString();
      var channel = document.get("channels").get(ref.substring(ref.lastIndexOf('/') + 1));
      actions.computeIfAbsent(channel.get("address").asString(), ignored -> new TreeSet<>())
        .add(operation.getValue().get("action").asString());
    }

    return actions;
  }

  private String typeOf(JsonNode messages, String title) {
    for (var message : messages.properties())
      if (message.getValue().get("title").asString().equals(title))
        return message.getValue().get("x-eventcatalog-message-type").asString();
    throw new AssertionError("No message " + title);
  }

  private JsonNode documentOf(String group) throws Exception {
    var json = mvc.perform(get("/springwolf/docs/" + group))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return JsonMapper.builder().build().readTree(json);
  }
}
