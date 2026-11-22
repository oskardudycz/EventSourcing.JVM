package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.core.identifiers.EntityId;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItems;
import jakarta.persistence.Embeddable;
import java.util.EnumSet;
import java.util.UUID;

public record ShoppingCart(Id id, ProductItems productItems, Status status) {
    public static ShoppingCart initial() {
        return new ShoppingCart(null, ProductItems.empty(), null);
    }

    public boolean isCheckedOut() {
        return status == Status.CheckedOut;
    }

    boolean isClosed() {
        return status == null || status.isClosed();
    }

    public static ShoppingCart evolve(ShoppingCart state, ShoppingCartEvent event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart added ->
                new ShoppingCart(
                        state.status == null ? added.shoppingCartId() : state.id,
                        state.productItems.add(added.productItem()),
                        state.status == null ? Status.Pending : state.status);
            case ProductItemRemovedFromShoppingCart removed ->
                new ShoppingCart(state.id, state.productItems.remove(removed.productItem()), state.status);
            case ProductItemPriceChanged priceChanged ->
                new ShoppingCart(
                        state.id,
                        state.productItems.changeUnitPrice(priceChanged.productId(), priceChanged.newUnitPrice()),
                        state.status);
            case ShoppingCartCheckedOut ignored -> new ShoppingCart(state.id, state.productItems, Status.CheckedOut);
        };
    }

    @Embeddable
    public record Id(@JsonValue String value) implements EntityId {
        private static final String type = "cart";

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Id {
            Urns.requireType(value, type);
        }

        public static Id of(UUID id) {
            return new Id(Urns.of("ecommerce", type, id));
        }

        public static Id of(UUID customerId, int cartNumber) {
            return new Id(Urns.of("ecommerce", type, "%s-%d".formatted(customerId, cartNumber)));
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum Status {
        Pending,
        CheckedOut;

        public static final EnumSet<Status> Closed = EnumSet.of(CheckedOut);

        public boolean isClosed() {
            return Closed.contains(this);
        }
    }
}
