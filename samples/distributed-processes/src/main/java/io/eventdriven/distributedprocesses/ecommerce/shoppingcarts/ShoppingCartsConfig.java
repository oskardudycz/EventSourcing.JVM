package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaries;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public final class ShoppingCartsConfig {
  private ShoppingCartsConfig() {
  }

  public static ShoppingCartFacade configure(
    CommandBus commandBus,
    EventStore eventStore,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus,
    ProductPriceCalculator prices,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<ShoppingCart, ShoppingCartEvent, ShoppingCartId>(
      eventStore,
      ShoppingCartFacade::mapToStreamId,
      ShoppingCart::empty
    );

    var facade = new ShoppingCartFacade(store, prices, now);

    commandBus
      .handle(OpenShoppingCart.class, facade::open)
      .handle(AddProductItemToShoppingCart.class, facade::addProductItem)
      .handle(RemoveProductItemFromShoppingCart.class, facade::removeProductItem)
      .handle(ConfirmShoppingCart.class, facade::confirm)
      .handle(CancelShoppingCart.class, facade::cancel);

    var summaries = new ShoppingCartSummaries(new Database());

    // Projecting first, so that the forwarder enriches from a summary
    // that already has the cart's product items.
    internalEventBus
      .subscribeWithMetadata(ShoppingCartOpened.class, summaries::onOpened)
      .subscribeWithMetadata(ProductItemAddedToShoppingCart.class, summaries::onProductItemAdded)
      .subscribeWithMetadata(ProductItemRemovedFromShoppingCart.class, summaries::onProductItemRemoved)
      .subscribeWithMetadata(ShoppingCartConfirmed.class, summaries::onConfirmed)
      .subscribeWithMetadata(ShoppingCartCanceled.class, summaries::onCanceled);

    var forwarder = new ShoppingCartExternalEventForwarder(summaries, integrationEventBus);

    internalEventBus
      .subscribe(ShoppingCartConfirmed.class, forwarder::on);

    return facade;
  }
}
