package io.eventdriven.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("in-memory")
@SpringBootTest
@AutoConfigureMockMvc
public class ECommerceDocumentationTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private MessageBus messageBus;

    @Nested
    public class AsyncApiDocuments {
        private static final List<String> groups = List.of("shopping_carts", "orders", "payments", "shipments");

        @Test
        public void paymentsDocumentsItsRequestsResponsesAndInternalChannels() throws Exception {
            assertThat(channelsOf("payments"))
                    .isEqualTo(Map.of(
                            "payments-requests", Set.of("AuthorizePayment", "CapturePayment", "CancelPayment"),
                            "payments-responses", Set.of("PaymentAuthorized", "PaymentCaptured", "PaymentFailed"),
                            "payments-internal",
                                    Set.of(
                                            "ConfirmPaymentAuthorization",
                                            "ConfirmPaymentCapture",
                                            "FailPaymentCapture",
                                            "TimeOutPayment",
                                            "ExpirePaymentAuthorization")));
        }

        @Test
        public void shipmentsDocumentsItsRequestsResponsesAndInternalChannels() throws Exception {
            assertThat(channelsOf("shipments"))
                    .isEqualTo(Map.of(
                            "shipments-requests", Set.of("ReserveStock", "RequestFulfillment", "ReleaseStock"),
                            "shipments-responses", Set.of("StockReserved", "PackageWasSent", "ShipmentFailed"),
                            "shipments-internal", Set.of("ConfirmPackageSent", "RejectFulfillment", "DeliverPackage")));
        }

        @Test
        public void shoppingCartsDocumentsItsExternalEvents() throws Exception {
            assertThat(channelsOf("shopping_carts"))
                    .isEqualTo(Map.of("shopping_carts-external", Set.of("ShoppingCartCheckedOut")));
        }

        @Test
        public void ordersDocumentsItsInternalChannelAndItsOwnQueues() throws Exception {
            assertThat(channelsOf("orders"))
                    .isEqualTo(Map.of(
                            "orders-internal",
                                    Set.of(
                                            "PlaceOrder",
                                            "RecordOrderPaymentAuthorization",
                                            "RecordOrderStockReservation",
                                            "RecordOrderPackageSent",
                                            "RecordOrderPaymentCapture",
                                            "RecordOrderPaymentFailure",
                                            "RecordOrderShipmentFailure",
                                            "CloseUnpaidOrder"),
                            "orders-shopping_carts-external", Set.of("ShoppingCartCheckedOut"),
                            "orders-payments-responses",
                                    Set.of("PaymentAuthorized", "PaymentCaptured", "PaymentFailed"),
                            "orders-shipments-responses", Set.of("StockReserved", "PackageWasSent", "ShipmentFailed"),
                            "payments-requests", Set.of("AuthorizePayment", "CapturePayment", "CancelPayment"),
                            "shipments-requests", Set.of("ReserveStock", "RequestFulfillment", "ReleaseStock")));
        }

        @Test
        public void eachDocumentHasOnlyTheOperationsOfItsModule() throws Exception {
            assertThat(actionsOf("payments"))
                    .isEqualTo(Map.of(
                            "payments-requests", Set.of("receive"),
                            "payments-responses", Set.of("send"),
                            "payments-internal", Set.of("receive")));
            assertThat(actionsOf("shipments"))
                    .isEqualTo(Map.of(
                            "shipments-requests", Set.of("receive"),
                            "shipments-responses", Set.of("send"),
                            "shipments-internal", Set.of("receive")));
            assertThat(actionsOf("shopping_carts")).isEqualTo(Map.of("shopping_carts-external", Set.of("send")));
            assertThat(actionsOf("orders"))
                    .isEqualTo(Map.of(
                            "orders-internal", Set.of("send", "receive"),
                            "orders-shopping_carts-external", Set.of("receive"),
                            "orders-payments-responses", Set.of("receive"),
                            "orders-shipments-responses", Set.of("receive"),
                            "payments-requests", Set.of("send"),
                            "shipments-requests", Set.of("send")));
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
        public void asyncApiListsTheKafkaAndRabbitMQServersNextToInMemory() throws Exception {
            for (var group : groups) {
                var document = documentOf(group);

                assertThat(document.get("servers").values())
                        .extracting(server -> server.get("protocol").asString())
                        .containsExactlyInAnyOrder("kafka", "amqp", "in-memory");
            }
        }

        @Test
        public void eachOperationAndMessageHasTheInMemoryBinding() throws Exception {
            for (var group : groups) {
                var document = documentOf(group);

                for (var operation : document.get("operations").values())
                    assertThat(operation.get("bindings").propertyNames())
                            .as(group + " operation")
                            .containsExactly("x-in-memory");
                for (var message : document.get("components").get("messages").values())
                    assertThat(message.get("bindings").propertyNames())
                            .as(group + " message")
                            .containsExactly("x-in-memory");
            }
        }

        @Test
        public void eachOperationHasOneMessageAndIsNamedAfterItsHandler() throws Exception {
            assertThat(operationIdsOf("orders"))
                    .contains(
                            "orders-shopping_carts-external_receive_onShoppingCartCheckedOut",
                            "orders-internal_send_onShoppingCartCheckedOut",
                            "payments-requests_send_onOrderPlaced",
                            "shipments-requests_send_onOrderPlaced",
                            "orders-payments-responses_receive_onPaymentAuthorized",
                            "payments-requests_send_onOrderCanceled");
            for (var group : groups)
                for (var operation : documentOf(group).get("operations").properties())
                    assertThat(operation.getValue().get("messages").size())
                            .as(group + " " + operation.getKey())
                            .isEqualTo(1);
        }

        @Test
        public void eachOperationAndMessageIsDescribed() throws Exception {
            for (var group : groups) {
                var document = documentOf(group);

                for (var operation : document.get("operations").properties())
                    assertThat(operation.getValue().get("description").asString())
                            .as(group + " " + operation.getKey())
                            .isNotBlank()
                            .isNotEqualTo("Auto-generated description");
                for (var message : document.get("components").get("messages").properties())
                    assertThat(message.getValue().path("description").asString(""))
                            .as(group + " " + message.getKey())
                            .isNotBlank();
            }
        }

        @Test
        public void eachDocumentDescribesItsModule() throws Exception {
            var descriptions = new TreeSet<String>();

            for (var group : groups) {
                var description =
                        documentOf(group).get("info").path("description").asString("");
                assertThat(description).as(group).isNotBlank();
                descriptions.add(description);
            }

            assertThat(descriptions).hasSize(groups.size());
        }

        @Test
        public void theDocumentsAreAsyncApi31AndTheUiIsServed() throws Exception {
            assertThat(documentOf("payments").get("asyncapi").asString()).isEqualTo("3.1.0");
            mvc.perform(get("/springwolf/asyncapi-ui.html")).andExpect(status().isOk());
        }

        private Set<String> operationIdsOf(String group) throws Exception {
            return new TreeSet<>(documentOf(group).get("operations").propertyNames());
        }

        private Map<String, Set<String>> channelsOf(String group) throws Exception {
            var document = documentOf(group);
            var channels = new TreeMap<String, Set<String>>();

            for (var channel : document.get("channels").properties()) {
                var messages = new TreeSet<String>();
                for (var message : channel.getValue().get("messages").properties()) {
                    var ref = message.getValue().get("$ref").asString();
                    var component =
                            document.get("components").get("messages").get(ref.substring(ref.lastIndexOf('/') + 1));
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
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            return JsonMapper.builder().build().readTree(json);
        }
    }

    @Nested
    public class OpenApiDocuments {
        @Test
        public void theOpenApiDocumentDescribesTheCustomersEndpointsOnly() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post")
                            .exists())
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session'].post")
                            .exists())
                    .andExpect(
                            jsonPath("$.paths['/sandbox/checkout/{sessionId}']").doesNotExist())
                    .andExpect(jsonPath("$.paths['/shop']").doesNotExist());
        }

        @Test
        public void eachModuleWithEndpointsHasItsOwnOpenApiDocument() throws Exception {
            mvc.perform(get("/v3/api-docs/shopping_carts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout']")
                            .exists())
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders']")
                            .doesNotExist());
            mvc.perform(get("/v3/api-docs/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session']")
                            .exists())
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current']")
                            .doesNotExist());
        }

        @Test
        public void theOperationsAreNamedAfterTheCommandsAndTheViews() throws Exception {
            mvc.perform(get("/v3/api-docs/shopping_carts"))
                    .andExpect(jsonPath("$.info.title").value("E-Commerce"))
                    .andExpect(jsonPath("$.info.version").value("1.0.0"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current'].get.operationId")
                            .value("Cart"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/product-items'].post.operationId")
                            .value("AddProductItemToShoppingCart"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/product-items'].delete.operationId")
                            .value("RemoveProductItemFromShoppingCart"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post.operationId")
                            .value("CheckOutShoppingCart"));
            mvc.perform(get("/v3/api-docs/orders"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders'].get.operationId")
                            .value("CustomerOrders"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders/{orderId}'].get.operationId")
                            .value("OrderStatus"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders/{orderId}/cancel'].post.operationId")
                            .value("CancelOrder"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/retry-payment'].post.operationId")
                            .value("RetryOrderPayment"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session'].post.operationId")
                            .value("CreateCheckoutSession"));
        }

        @Test
        public void readsAreQueriesAndChangesAreCommandsForEventCatalog() throws Exception {
            mvc.perform(get("/v3/api-docs/shopping_carts"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current'].get['x-eventcatalog-message-type']")
                            .value("query"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/product-items'].post['x-eventcatalog-message-type']")
                            .value("command"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/product-items'].delete['x-eventcatalog-message-type']")
                            .value("command"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post['x-eventcatalog-message-type']")
                            .value("command"));
            mvc.perform(get("/v3/api-docs/orders"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders'].get['x-eventcatalog-message-type']")
                            .value("query"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}'].get['x-eventcatalog-message-type']")
                            .value("query"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/cancel'].post['x-eventcatalog-message-type']")
                            .value("command"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/retry-payment'].post['x-eventcatalog-message-type']")
                            .value("command"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session'].post['x-eventcatalog-message-type']")
                            .value("command"));
        }

        @Test
        public void theOperationsAreTaggedWithTheirModule() throws Exception {
            mvc.perform(get("/v3/api-docs/shopping_carts"))
                    .andExpect(jsonPath("$.tags[0].name").value("Shopping carts"))
                    .andExpect(jsonPath("$.tags[0].description")
                            .value("The customer's current shopping cart and its checkout"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current'].get.tags")
                            .value("Shopping carts"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post.tags")
                            .value("Shopping carts"));
            mvc.perform(get("/v3/api-docs/orders"))
                    .andExpect(jsonPath("$.tags[0].name").value("Orders"))
                    .andExpect(
                            jsonPath("$.tags[0].description")
                                    .value(
                                            "The customer's orders: their status, canceling, paying again and the checkout page of a payment"))
                    .andExpect(jsonPath("$.paths['/customers/{customerId}/orders'].get.tags")
                            .value("Orders"))
                    .andExpect(jsonPath(
                                    "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session'].post.tags")
                            .value("Orders"));
        }

        @Test
        public void swaggerUiIsServed() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    public class PublishingFromSpringwolf {
        @Test
        public void theUiCanPublishOnTheInMemoryBus() throws Exception {
            mvc.perform(get("/springwolf/plugin/x-in-memory/publish")).andExpect(status().isOk());
        }

        @Test
        public void aMessagePublishedFromTheUiReachesTheBus() throws Exception {
            var shipmentId = Shipment.Id.of(UUID.randomUUID());
            var published = new ArrayList<Object>();
            messageBus.use(published::add);

            mvc.perform(post("/springwolf/plugin/x-in-memory/publish")
                            .param("topic", "shipments-internal")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
              {
                "type": "io.eventdriven.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent",
                "payload": "{\\"shipmentId\\": \\"%s\\", \\"carrier\\": \\"DHL\\", \\"trackingNumber\\": \\"123\\"}"
              }
              """.formatted(shipmentId)))
                    .andExpect(status().isOk());

            assertThat(published).contains(new ConfirmPackageSent(shipmentId, "DHL", "123"));
        }
    }
}
