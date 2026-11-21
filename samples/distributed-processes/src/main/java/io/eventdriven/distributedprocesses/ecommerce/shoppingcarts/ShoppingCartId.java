package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ShoppingCartId(@JsonValue String value) implements EntityId {
    private static final String type = "cart";

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ShoppingCartId {
        Urns.requireType(value, type);
    }

    public static ShoppingCartId of(UUID id) {
        return new ShoppingCartId(Urns.of("ecommerce", type, id));
    }

    public static ShoppingCartId of(UUID customerId, int cartNumber) {
        return new ShoppingCartId(Urns.of("ecommerce", type, "%s-%d".formatted(customerId, cartNumber)));
    }

    @Override
    public String toString() {
        return value;
    }
}
