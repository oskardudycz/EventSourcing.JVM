package io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency;

import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.Projections.ShoppingCartDetails;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.Projections.ShoppingCartDetailsProjection;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.Projections.ShoppingCartShortInfo;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.Projections.ShoppingCartShortInfoProjection;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.tools.Database;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_eventual_consistency.tools.EventStore;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectionsTests {
  public sealed interface ShoppingCartEvent {
    record ShoppingCartOpened(
      UUID shoppingCartId,
      UUID clientId
    ) implements ShoppingCartEvent {
    }

    record ProductItemAddedToShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ProductItemRemovedFromShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartConfirmed(
      UUID shoppingCartId,
      OffsetDateTime confirmedAt
    ) implements ShoppingCartEvent {
    }

    record ShoppingCartCanceled(
      UUID shoppingCartId,
      OffsetDateTime canceledAt
    ) implements ShoppingCartEvent {
    }
  }

  public record ProductItem(
    UUID productId,
    int quantity) {
  }

  public record PricedProductItem(
    UUID productId,
    int quantity,
    double unitPrice
  ) {
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
    eventStore.subscribe(ShoppingCartEvent.ProductItemAddedToShoppingCart.class, shoppingCartDetailsProjection::handleProductAdded);
    eventStore.subscribe(ShoppingCartEvent.ProductItemRemovedFromShoppingCart.class, shoppingCartDetailsProjection::handleProductRemoved);
    eventStore.subscribe(ShoppingCartEvent.ShoppingCartConfirmed.class, shoppingCartDetailsProjection::handleConfirmed);
    eventStore.subscribe(ShoppingCartEvent.ShoppingCartCanceled.class, shoppingCartDetailsProjection::handleCanceled);

    var shoppingCartShortInfoProjection = new ShoppingCartShortInfoProjection(database);

    eventStore.subscribe(ShoppingCartEvent.ShoppingCartOpened.class, shoppingCartShortInfoProjection::handleOpened);
    eventStore.subscribe(ShoppingCartEvent.ProductItemAddedToShoppingCart.class, shoppingCartShortInfoProjection::handleProductAdded);
    eventStore.subscribe(ShoppingCartEvent.ProductItemRemovedFromShoppingCart.class, shoppingCartShortInfoProjection::handleProductRemoved);
    eventStore.subscribe(ShoppingCartEvent.ShoppingCartConfirmed.class, shoppingCartShortInfoProjection::handleConfirmed);
    eventStore.subscribe(ShoppingCartEvent.ShoppingCartCanceled.class, shoppingCartShortInfoProjection::handleCanceled);

    // first confirmed
    eventStore.append(shoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(shoppingCartId, clientId));
    eventStore.append(shoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes));
    eventStore.append(shoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(shoppingCartId, tShirt));
    eventStore.append(shoppingCartId, new ShoppingCartEvent.ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes));
    eventStore.append(shoppingCartId, new ShoppingCartEvent.ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()));

    // cancelled
    eventStore.append(cancelledShoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(cancelledShoppingCartId, clientId));
    eventStore.append(cancelledShoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(cancelledShoppingCartId, dress));
    eventStore.append(cancelledShoppingCartId, new ShoppingCartEvent.ShoppingCartCanceled(cancelledShoppingCartId, OffsetDateTime.now()));

    // confirmed but other client
    eventStore.append(otherClientShoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(otherClientShoppingCartId, otherClientId));
    eventStore.append(otherClientShoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(otherClientShoppingCartId, dress));
    eventStore.append(otherClientShoppingCartId, new ShoppingCartEvent.ShoppingCartConfirmed(otherClientShoppingCartId, OffsetDateTime.now()));

    // second confirmed
    eventStore.append(otherConfirmedShoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(otherConfirmedShoppingCartId, clientId));
    eventStore.append(otherConfirmedShoppingCartId, new ShoppingCartEvent.ProductItemAddedToShoppingCart(otherConfirmedShoppingCartId, trousers));
    eventStore.append(otherConfirmedShoppingCartId, new ShoppingCartEvent.ShoppingCartConfirmed(otherConfirmedShoppingCartId, OffsetDateTime.now()));

    // first pending
    eventStore.append(otherPendingShoppingCartId, new ShoppingCartEvent.ShoppingCartOpened(otherPendingShoppingCartId, clientId));

    // first confirmed
    var shoppingCart = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartDetails.class, shoppingCartId, 4);
    assertTrue(shoppingCart.isPresent());
    assertEquals(shoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(2, shoppingCart.get().getProductItems().size());
    assertEquals(pairOfShoes, shoppingCart.get().getProductItems().get(0));
    assertEquals(tShirt, shoppingCart.get().getProductItems().get(1));

    var shoppingCartShortInfo = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartShortInfo.class, shoppingCartId, 4);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // cancelled
    shoppingCart = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartDetails.class, cancelledShoppingCartId, 2);
    assertTrue(shoppingCart.isPresent());
    assertEquals(cancelledShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Canceled, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(dress, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartShortInfo.class, cancelledShoppingCartId,2);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // confirmed but other client
    shoppingCart = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartDetails.class, otherClientShoppingCartId,2);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherClientShoppingCartId, shoppingCart.get().getId());
    assertEquals(otherClientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(dress, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartShortInfo.class, otherClientShoppingCartId,2);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // second confirmed
    shoppingCart = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartDetails.class, otherConfirmedShoppingCartId,2);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherConfirmedShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(trousers, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartShortInfo.class, otherConfirmedShoppingCartId,2);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // first pending
    shoppingCart = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartDetails.class, otherPendingShoppingCartId, 0);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherPendingShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Pending, shoppingCart.get().getStatus());
    assertEquals(0, shoppingCart.get().getProductItems().size());

    shoppingCartShortInfo = database.getExpectingGreaterOrEqualVersionWithRetries(ShoppingCartShortInfo.class, otherPendingShoppingCartId, 0);
    assertTrue(shoppingCartShortInfo.isPresent());
    assertEquals(otherPendingShoppingCartId, shoppingCartShortInfo.get().getId());
    assertEquals(clientId, shoppingCartShortInfo.get().getClientId());
  }
}
