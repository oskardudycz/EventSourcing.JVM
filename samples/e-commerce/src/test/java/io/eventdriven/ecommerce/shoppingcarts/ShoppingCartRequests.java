package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.testing.api.ApiE2ESpecification.TestRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class ShoppingCartRequests {
    private ShoppingCartRequests() {}

    public static TestRequest addProductItem(UUID customerId, UUID productId, int quantity) {
        return addProductItem(customerId, Map.of("productId", productId, "quantity", quantity));
    }

    public static TestRequest addProductItem(UUID customerId, Map<String, ?> productItem) {
        return request -> request.post()
                .uri("/customers/{customerId}/shopping-carts/current/product-items", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(productItem);
    }

    public static TestRequest removeProductItem(UUID customerId, UUID productId, int quantity, String ifMatch) {
        return request -> request.delete()
                .uri(
                        "/customers/{customerId}/shopping-carts/current/product-items?productId={productId}&quantity={quantity}",
                        customerId,
                        productId,
                        quantity)
                .header(HttpHeaders.IF_MATCH, ifMatch);
    }

    public static TestRequest removeProductItem(UUID customerId, UUID productId, int quantity) {
        return request -> request.delete()
                .uri(
                        "/customers/{customerId}/shopping-carts/current/product-items?productId={productId}&quantity={quantity}",
                        customerId,
                        productId,
                        quantity);
    }

    public static TestRequest checkOut(UUID customerId, String ifMatch) {
        return request -> request.post()
                .uri("/customers/{customerId}/shopping-carts/current/checkout", customerId)
                .header(HttpHeaders.IF_MATCH, ifMatch);
    }

    public static TestRequest checkOut(UUID customerId) {
        return request -> request.post().uri("/customers/{customerId}/shopping-carts/current/checkout", customerId);
    }

    public static TestRequest getCart(UUID customerId) {
        return request -> request.get().uri("/customers/{customerId}/shopping-carts/current", customerId);
    }

    public static TestRequest productsPage(UUID customerId) {
        return request -> request.get().uri("/shop/customers/{customerId}/products", customerId);
    }

    public static TestRequest cartPage(UUID customerId) {
        return request -> request.get().uri("/shop/customers/{customerId}/cart", customerId);
    }

    public static TestRequest addToCartOnPage(UUID customerId, UUID productId, int quantity) {
        return request -> request.post()
                .uri("/shop/customers/{customerId}/cart/items", customerId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("productId=%s&quantity=%s".formatted(productId, quantity));
    }

    public static TestRequest removeFromCartOnPage(UUID customerId, UUID productId, int quantity, long version) {
        return request -> request.post()
                .uri("/shop/customers/{customerId}/cart/items/{productId}/remove", customerId, productId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("quantity=%s&version=%s".formatted(quantity, version));
    }

    public static TestRequest checkOutOnPage(UUID customerId, long version) {
        return request -> request.post()
                .uri("/shop/customers/{customerId}/cart/checkout", customerId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("version=%s".formatted(version));
    }
}
