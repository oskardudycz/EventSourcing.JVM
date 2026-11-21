package io.eventdriven.distributedprocesses.ecommerce.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("in-memory")
@SpringBootTest
@AutoConfigureMockMvc
public class OpenApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    public void theOpenApiDocumentDescribesTheCustomersEndpointsOnly() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/sandbox/checkout/{sessionId}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/shop']").doesNotExist());
    }

    @Test
    public void eachModuleWithEndpointsHasItsOwnOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs/shopping_carts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout']")
                        .exists())
                .andExpect(jsonPath("$.paths['/customers/{customerId}/orders']").doesNotExist());
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
                .andExpect(
                        jsonPath("$.paths['/customers/{customerId}/shopping-carts/current/checkout'].post.operationId")
                                .value("CheckOutShoppingCart"));
        mvc.perform(get("/v3/api-docs/orders"))
                .andExpect(jsonPath("$.paths['/customers/{customerId}/orders'].get.operationId")
                        .value("CustomerOrders"))
                .andExpect(jsonPath("$.paths['/customers/{customerId}/orders/{orderId}'].get.operationId")
                        .value("OrderStatus"))
                .andExpect(jsonPath("$.paths['/customers/{customerId}/orders/{orderId}/cancel'].post.operationId")
                        .value("CancelOrder"))
                .andExpect(
                        jsonPath("$.paths['/customers/{customerId}/orders/{orderId}/retry-payment'].post.operationId")
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
