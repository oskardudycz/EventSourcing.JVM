package io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream;

import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.Projections.ShoppingCartDetails;
import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.Projections.ShoppingCartShortInfo;
import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.tools.Database;
import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.tools.EventStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream.ProjectionsTests.ShoppingCartEvent.*;
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

  @Tag("Exercise")
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
    // 1. Register here your event handlers using `eventBus.Register`.
    // 2. Store results in database.

    // first confirmed
    eventStore.append(shoppingCartId, new ShoppingCartOpened(shoppingCartId, clientId));
    eventStore.append(shoppingCartId, new ProductItemAddedToShoppingCart(shoppingCartId, twoPairsOfShoes));
    eventStore.append(shoppingCartId, new ProductItemAddedToShoppingCart(shoppingCartId, tShirt));
    eventStore.append(shoppingCartId, new ProductItemRemovedFromShoppingCart(shoppingCartId, pairOfShoes));
    eventStore.append(shoppingCartId, new ShoppingCartConfirmed(shoppingCartId, OffsetDateTime.now()));

    // cancelled
    eventStore.append(cancelledShoppingCartId, new ShoppingCartOpened(cancelledShoppingCartId, clientId));
    eventStore.append(cancelledShoppingCartId, new ProductItemAddedToShoppingCart(cancelledShoppingCartId, dress));
    eventStore.append(cancelledShoppingCartId, new ShoppingCartCanceled(cancelledShoppingCartId, OffsetDateTime.now()));

    // confirmed but other client
    eventStore.append(otherClientShoppingCartId, new ShoppingCartOpened(otherClientShoppingCartId, otherClientId));
    eventStore.append(otherClientShoppingCartId, new ProductItemAddedToShoppingCart(otherClientShoppingCartId, dress));
    eventStore.append(otherClientShoppingCartId, new ShoppingCartConfirmed(otherClientShoppingCartId, OffsetDateTime.now()));

    // second confirmed
    eventStore.append(otherConfirmedShoppingCartId, new ShoppingCartOpened(otherConfirmedShoppingCartId, clientId));
    eventStore.append(otherConfirmedShoppingCartId, new ProductItemAddedToShoppingCart(otherConfirmedShoppingCartId, trousers));
    eventStore.append(otherConfirmedShoppingCartId, new ShoppingCartConfirmed(otherConfirmedShoppingCartId, OffsetDateTime.now()));

    // first pending
    eventStore.append(otherPendingShoppingCartId, new ShoppingCartOpened(otherPendingShoppingCartId, clientId));

    // first confirmed
    var shoppingCart = database.get(ShoppingCartDetails.class, shoppingCartId);
    assertTrue(shoppingCart.isPresent());
    assertEquals(shoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(2, shoppingCart.get().getProductItems().size());
    assertEquals(pairOfShoes, shoppingCart.get().getProductItems().get(0));
    assertEquals(tShirt, shoppingCart.get().getProductItems().get(1));

    var shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, shoppingCartId);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // cancelled
    shoppingCart = database.get(ShoppingCartDetails.class, cancelledShoppingCartId);
    assertTrue(shoppingCart.isPresent());
    assertEquals(cancelledShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Canceled, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(dress, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, cancelledShoppingCartId);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // confirmed but other client
    shoppingCart = database.get(ShoppingCartDetails.class, otherClientShoppingCartId);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherClientShoppingCartId, shoppingCart.get().getId());
    assertEquals(otherClientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(dress, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherClientShoppingCartId);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // second confirmed
    shoppingCart = database.get(ShoppingCartDetails.class, otherConfirmedShoppingCartId);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherConfirmedShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.get().getStatus());
    assertEquals(1, shoppingCart.get().getProductItems().size());
    assertEquals(trousers, shoppingCart.get().getProductItems().get(0));

    shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherConfirmedShoppingCartId);
    assertTrue(shoppingCartShortInfo.isEmpty());

    // first pending
    shoppingCart = database.get(ShoppingCartDetails.class, otherPendingShoppingCartId);
    assertTrue(shoppingCart.isPresent());
    assertEquals(otherPendingShoppingCartId, shoppingCart.get().getId());
    assertEquals(clientId, shoppingCart.get().getClientId());
    assertEquals(ShoppingCartStatus.Pending, shoppingCart.get().getStatus());
    assertEquals(0, shoppingCart.get().getProductItems().size());

    shoppingCartShortInfo = database.get(ShoppingCartShortInfo.class, otherPendingShoppingCartId);
    assertTrue(shoppingCartShortInfo.isPresent());
    assertEquals(otherPendingShoppingCartId, shoppingCartShortInfo.get().getId());
    assertEquals(clientId, shoppingCartShortInfo.get().getClientId());
  }
}
