package io.eventdriven.ecommerce.shipments;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.testing.api.ApiE2ESpecification.TestRequest;
import java.util.UUID;
import org.springframework.http.MediaType;

public final class ShipmentRequests {
    private ShipmentRequests() {}

    public static final String warehouse = "/sandbox/warehouse";

    public static String packageAction(Order.Id orderId, String action) {
        return "%s/shipments/%s/%s".formatted(warehouse, orderId.tail(), action);
    }

    public static TestRequest warehousePage() {
        return request -> request.get().uri(warehouse);
    }

    public static TestRequest markInStock(UUID productId, boolean inStock) {
        return request -> request.post()
                .uri(warehouse + "/products/{productId}/stock", productId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("inStock=" + inStock);
    }

    public static TestRequest sendPackage(Order.Id orderId) {
        return request -> request.post().uri(packageAction(orderId, "send"));
    }

    public static TestRequest rejectPackage(Order.Id orderId) {
        return request -> request.post().uri(packageAction(orderId, "reject"));
    }

    public static TestRequest deliverPackage(Order.Id orderId) {
        return request -> request.post().uri(packageAction(orderId, "deliver"));
    }
}
