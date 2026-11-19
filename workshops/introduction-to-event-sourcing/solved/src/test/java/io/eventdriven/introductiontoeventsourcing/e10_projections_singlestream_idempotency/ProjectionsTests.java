package io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.Projections.ShoppingCartDetails;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.Projections.ShoppingCartDetailsProjection;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.Projections.ShoppingCartShortInfo;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.Projections.ShoppingCartShortInfoProjection;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.tools.Database;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.tools.EventStore;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ProjectionsTests {
    public sealed interface ShoppingCartEvent {
        record ShoppingCartOpened(UUID shoppingCartId, UUID clientId) implements ShoppingCartEvent {}

        record ProductItemAddedToShoppingCart(UUID shoppingCartId, PricedProductItem productItem)
                implements ShoppingCartEvent {}

        record ProductItemRemovedFromShoppingCart(UUID shoppingCartId, PricedProductItem productItem)
                implements ShoppingCartEvent {}

        record ShoppingCartConfirmed(UUID shoppingCartId, OffsetDateTime confirmedAt) implements ShoppingCartEvent {}

        record ShoppingCartCanceled(UUID shoppingCartId, OffsetDateTime canceledAt) implements ShoppingCartEvent {}
    }

    public record ProductItem(UUID productId, int quantity) {}

    public record PricedProductItem(UUID productId, int quantity, double unitPrice) {
        public double totalAmount() {
            return quantity * unitPrice;
        }
    }

    public enum ShoppingCartStatus {
        Pending,
        Confirmed,
        Canceled
    }

    @Test
    public void GettingReadModels_ForStoredSequenceOfEvents_ShouldSucceed() {
        var shoppingCartId = UUID.randomUUID();

        var clientId = UUID.randomUUID();
        var shoesId = UUID.randomUUID();
        var tShirtId = UUID.randomUUID();
        var dressId = UUID.randomUUID();
        var trousersId = UUID.randomUUID();

        var twoPairsOfShoes = new PricedProductItem(shoesId, 2, 100);
        var pairOfShoes = new PricedProductItem(shoesId, 1, 100);
        var tShirt = new PricedProductItem(tShirtId, 1, 50);
        var dress = new PricedProductItem(dressId, 3, 150);
        var trousers = new PricedProductItem(trousersId, 1, 300);

        var cancelledShoppingCartId = UUID.randomUUID();
        var otherClientShoppingCartId = UUID.randomUUID();
        var otherConfirmedShoppingCartId = UUID.randomUUID();
        var otherPendingShoppingCartId = UUID.randomUUID();
        var otherClientId = UUID.randomUUID();

        var eventStore = new EventStore();
        var database = new Database();

        // TODO:
        // 1. Register here your event handlers using `eventStore.subscribe`.
        // 2. Store results in database.

        var shoppingCartDetailsProjection = new ShoppingCartDetailsProjection(database);

        eventStore.subscribe(ShoppingCartEvent.ShoppingCartOpened.class, shoppingCartDetailsProjection::handleOpened);
        eventStore.subscribe(
                ShoppingCartEvent.ProductItemAddedToShoppingCart.class,
                shoppingCartDetailsProjection::handleProductAdded);
        eventStore.subscribe(
                ShoppingCartEvent.ProductItemRemovedFromShoppingCart.class,
                shoppingCartDetailsProjection::handleProductRemoved);
        eventStore.subscribe(
                ShoppingCartEvent.ShoppingCartConfirmed.class, shoppingCartDetailsProjection::handleConfirmed);
        eventStore.subscribe(
                ShoppingCartEvent.ShoppingCartCanceled.class, shoppingCartDetailsProjection::handleCanceled);

        var shoppingCartShortInfoProjection = new ShoppingCartShortInfoProjection(database);

        eventStore.subscribe(ShoppingCartEvent.ShoppingCartOpened.class, shoppingCartShortInfoProjection::handleOpened);
        eventStore.subscribe(
                ShoppingCartEvent.ProductItemAddedToShoppingCart.class,
                shoppingCartShortInfoProjection::handleProductAdded);
        eventStore.subscribe(
                ShoppingCartEvent.ProductItemRemovedFromShoppingCart.class,
                shoppingCartShortInfoProjection::handleProductRemoved);
        eventStore.subscribe(
                ShoppingCartEvent.ShoppingCartConfirmed.class, shoppingCartShortInfoProjection::handleConfirmed);
        eventStore.subscribe(
                ShoppingCartEvent.ShoppingCartCanceled.class, shoppingCartShortInfoProjection::handleCanceled);

        // first confirmed
        eventStore.append(shoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(shoppingCartId, clientId));
        eventStore.append(
                shoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes));
        eventStore.append(shoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, tShirt));
        eventStore.append(
                shoppingCartId, new ShoppingCartEvent.ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes));
        eventStore.append(
                shoppingCartId, new ShoppingCartEvent.ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()));

        // cancelled
        eventStore.append(
                cancelledShoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(cancelledShoppingCartId, clientId));
        eventStore.append(
                cancelledShoppingCartId,
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(cancelledShoppingCartId, dress));
        eventStore.append(
                cancelledShoppingCartId,
                new ShoppingCartEvent.ShoppingCartCanceled(cancelledShoppingCartId, OffsetDateTime.now()));

        // confirmed but other client
        eventStore.append(
                otherClientShoppingCartId,
                new ShoppingCartEvent.ShoppingCartOpened(otherClientShoppingCartId, otherClientId));
        eventStore.append(
                otherClientShoppingCartId,
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(otherClientShoppingCartId, dress));
        eventStore.append(
                otherClientShoppingCartId,
                new ShoppingCartEvent.ShoppingCartConfirmed(otherClientShoppingCartId, OffsetDateTime.now()));

        // second confirmed
        eventStore.append(
                otherConfirmedShoppingCartId,
                new ShoppingCartEvent.ShoppingCartOpened(otherConfirmedShoppingCartId, clientId));
        eventStore.append(
                otherConfirmedShoppingCartId,
                new ShoppingCartEvent.ProductItemAddedToShoppingCart(otherConfirmedShoppingCartId, trousers));
        eventStore.append(
                otherConfirmedShoppingCartId,
                new ShoppingCartEvent.ShoppingCartConfirmed(otherConfirmedShoppingCartId, OffsetDateTime.now()));

        // first pending
        eventStore.append(
                otherPendingShoppingCartId,
                new ShoppingCartEvent.ShoppingCartOpened(otherPendingShoppingCartId, clientId));

        // first confirmed
        var shoppingCart = database.get(ShoppingCartDetails.class, shoppingCartId);
        assertThat(shoppingCart.isPresent()).isTrue();
        assertThat(shoppingCart.get().getId()).isEqualTo(shoppingCartId);
        assertThat(shoppingCart.get().getClientId()).isEqualTo(clientId);
        assertThat(shoppingCart.get().getStatus()).isEqualTo(ShoppingCartStatus.Confirmed);
        assertThat(shoppingCart.get().getProductItems().size()).isEqualTo(2);
        assertThat(shoppingCart.get().getProductItems().get(0)).isEqualTo(pairOfShoes);
        assertThat(shoppingCart.get().getProductItems().get(1)).isEqualTo(tShirt);

        var shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, shoppingCartId);
        assertThat(shoppingCartShortInfo.isEmpty()).isTrue();

        // cancelled
        shoppingCart = database.get(ShoppingCartDetails.class, cancelledShoppingCartId);
        assertThat(shoppingCart.isPresent()).isTrue();
        assertThat(shoppingCart.get().getId()).isEqualTo(cancelledShoppingCartId);
        assertThat(shoppingCart.get().getClientId()).isEqualTo(clientId);
        assertThat(shoppingCart.get().getStatus()).isEqualTo(ShoppingCartStatus.Canceled);
        assertThat(shoppingCart.get().getProductItems().size()).isEqualTo(1);
        assertThat(shoppingCart.get().getProductItems().get(0)).isEqualTo(dress);

        shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, cancelledShoppingCartId);
        assertThat(shoppingCartShortInfo.isEmpty()).isTrue();

        // confirmed but other client
        shoppingCart = database.get(ShoppingCartDetails.class, otherClientShoppingCartId);
        assertThat(shoppingCart.isPresent()).isTrue();
        assertThat(shoppingCart.get().getId()).isEqualTo(otherClientShoppingCartId);
        assertThat(shoppingCart.get().getClientId()).isEqualTo(otherClientId);
        assertThat(shoppingCart.get().getStatus()).isEqualTo(ShoppingCartStatus.Confirmed);
        assertThat(shoppingCart.get().getProductItems().size()).isEqualTo(1);
        assertThat(shoppingCart.get().getProductItems().get(0)).isEqualTo(dress);

        shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherClientShoppingCartId);
        assertThat(shoppingCartShortInfo.isEmpty()).isTrue();

        // second confirmed
        shoppingCart = database.get(ShoppingCartDetails.class, otherConfirmedShoppingCartId);
        assertThat(shoppingCart.isPresent()).isTrue();
        assertThat(shoppingCart.get().getId()).isEqualTo(otherConfirmedShoppingCartId);
        assertThat(shoppingCart.get().getClientId()).isEqualTo(clientId);
        assertThat(shoppingCart.get().getStatus()).isEqualTo(ShoppingCartStatus.Confirmed);
        assertThat(shoppingCart.get().getProductItems().size()).isEqualTo(1);
        assertThat(shoppingCart.get().getProductItems().get(0)).isEqualTo(trousers);

        shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherConfirmedShoppingCartId);
        assertThat(shoppingCartShortInfo.isEmpty()).isTrue();

        // first pending
        shoppingCart = database.get(ShoppingCartDetails.class, otherPendingShoppingCartId);
        assertThat(shoppingCart.isPresent()).isTrue();
        assertThat(shoppingCart.get().getId()).isEqualTo(otherPendingShoppingCartId);
        assertThat(shoppingCart.get().getClientId()).isEqualTo(clientId);
        assertThat(shoppingCart.get().getStatus()).isEqualTo(ShoppingCartStatus.Pending);
        assertThat(shoppingCart.get().getProductItems().size()).isEqualTo(0);

        shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherPendingShoppingCartId);
        assertThat(shoppingCartShortInfo.isPresent()).isTrue();
        assertThat(shoppingCartShortInfo.get().getId()).isEqualTo(otherPendingShoppingCartId);
        assertThat(shoppingCartShortInfo.get().getClientId()).isEqualTo(clientId);
    }
}
