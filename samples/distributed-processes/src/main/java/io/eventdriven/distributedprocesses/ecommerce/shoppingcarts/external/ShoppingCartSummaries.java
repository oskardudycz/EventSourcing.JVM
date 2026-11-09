package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartStatus;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartSummaries {
  private final Database database;

  public ShoppingCartSummaries(Database database) {
    this.database = database;
  }

  public void onOpened(EventEnvelope<ShoppingCartOpened> event) {
    database.store(
      ShoppingCartSummary.class,
      event.data().shoppingCartId(),
      event.metadata().streamPosition(),
      new ShoppingCartSummary(
        event.data().shoppingCartId(),
        event.data().clientId(),
        new PricedProductItem[0],
        0,
        ShoppingCartStatus.Pending
      )
    );
  }

  public void onProductItemAdded(EventEnvelope<ProductItemAddedToShoppingCart> event) {
    database.getAndUpdate(
      ShoppingCartSummary.class,
      event.data().shoppingCartId(),
      event.metadata().streamPosition(),
      summary -> withProductItems(summary, productItemsOf(summary).add(event.data().productItem()))
    );
  }

  public void onProductItemRemoved(EventEnvelope<ProductItemRemovedFromShoppingCart> event) {
    database.getAndUpdate(
      ShoppingCartSummary.class,
      event.data().shoppingCartId(),
      event.metadata().streamPosition(),
      summary -> withProductItems(summary, productItemsOf(summary).remove(event.data().productItem()))
    );
  }

  public void onConfirmed(EventEnvelope<ShoppingCartConfirmed> event) {
    setStatus(event.data().shoppingCartId(), event.metadata().streamPosition(), ShoppingCartStatus.Confirmed);
  }

  public void onCanceled(EventEnvelope<ShoppingCartCanceled> event) {
    setStatus(event.data().shoppingCartId(), event.metadata().streamPosition(), ShoppingCartStatus.Canceled);
  }

  public Optional<ShoppingCartSummary> get(ShoppingCartId shoppingCartId) {
    return database.get(ShoppingCartSummary.class, shoppingCartId);
  }

  private void setStatus(ShoppingCartId shoppingCartId, long version, ShoppingCartStatus status) {
    database.getAndUpdate(
      ShoppingCartSummary.class,
      shoppingCartId,
      version,
      summary -> {
        summary.setStatus(status);

        return summary;
      }
    );
  }

  private static ShoppingCartSummary withProductItems(ShoppingCartSummary summary, ProductItems productItems) {
    summary.setProductItems(productItems.items().toArray(new PricedProductItem[0]));
    summary.setTotalPrice(
      productItems.items().stream()
        .mapToDouble(PricedProductItem::totalPrice)
        .sum()
    );

    return summary;
  }

  private static ProductItems productItemsOf(ShoppingCartSummary summary) {
    return new ProductItems(new ArrayList<>(Arrays.asList(summary.getProductItems())));
  }
}
