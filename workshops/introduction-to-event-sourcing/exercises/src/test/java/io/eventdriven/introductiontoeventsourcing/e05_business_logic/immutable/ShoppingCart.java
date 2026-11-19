package io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ShoppingCartEvent.*;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShoppingCart(
        UUID id,
        UUID clientId,
        Status status,
        ProductItems productItems,
        OffsetDateTime confirmedAt,
        OffsetDateTime canceledAt) {
    public enum Status {
        Pending,
        Confirmed,
        Canceled
    }

    public static ShoppingCart initial() {
        return new ShoppingCart(null, null, null, null, null, null);
    }

    public static ShoppingCart evolve(ShoppingCart state, ShoppingCartEvent event) {
        return switch (event) {
            case ShoppingCartOpened opened ->
                new ShoppingCart(
                        opened.shoppingCartId(), opened.clientId(), Status.Pending, ProductItems.empty(), null, null);
            case ProductItemAddedToShoppingCart(var shoppingCartId, var productItem) ->
                new ShoppingCart(
                        state.id,
                        state.clientId,
                        state.status,
                        state.productItems.add(productItem),
                        state.confirmedAt,
                        state.canceledAt);
            case ProductItemRemovedFromShoppingCart(var shoppingCartId, var productItem) ->
                new ShoppingCart(
                        state.id,
                        state.clientId,
                        state.status,
                        state.productItems.remove(productItem),
                        state.confirmedAt,
                        state.canceledAt);
            case ShoppingCartConfirmed _ ->
                new ShoppingCart(
                        state.id,
                        state.clientId,
                        Status.Confirmed,
                        state.productItems,
                        state.confirmedAt,
                        state.canceledAt);
            case ShoppingCartCanceled _ ->
                new ShoppingCart(
                        state.id,
                        state.clientId,
                        Status.Canceled,
                        state.productItems,
                        state.confirmedAt,
                        state.canceledAt);
        };
    }
}
