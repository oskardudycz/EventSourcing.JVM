package io.eventdriven.introductiontoeventsourcing.e07_application_logic.kurrentdb.mixed.app.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;

public final class ShoppingCartsRequests {
    public record Open(@NotNull UUID clientId) {}

    @Validated
    public record ProductItemRequest(
            @NotNull UUID productId, @NotNull Integer quantity) {}

    public record AddProduct(@NotNull ProductItemRequest productItem) {}
}
