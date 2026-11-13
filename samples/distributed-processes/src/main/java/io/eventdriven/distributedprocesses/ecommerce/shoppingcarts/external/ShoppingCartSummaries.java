package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartSummaries extends Projection<ShoppingCartSummary> {
  public ShoppingCartSummaries(Database database) {
    super(ShoppingCartSummary.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      ShoppingCartOpened.class,
      ProductItemAddedToShoppingCart.class,
      ProductItemRemovedFromShoppingCart.class,
      ShoppingCartConfirmed.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case ShoppingCartOpened e -> e.shoppingCartId();
      case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
      case ProductItemRemovedFromShoppingCart e -> e.shoppingCartId();
      case ShoppingCartConfirmed e -> e.shoppingCartId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable ShoppingCartSummary evolve(@Nullable ShoppingCartSummary current, Object event) {
    return switch (event) {
      case ShoppingCartOpened e -> new ShoppingCartSummary(e.clientId(), new PricedProductItem[0], 0);
      case ProductItemAddedToShoppingCart e -> withProductItems(current, productItemsOf(current).add(e.productItem()));
      case ProductItemRemovedFromShoppingCart e ->
        withProductItems(current, productItemsOf(current).remove(e.productItem()));
      default -> current;
    };
  }

  private static ShoppingCartSummary withProductItems(ShoppingCartSummary summary, ProductItems productItems) {
    return new ShoppingCartSummary(
      summary.getClientId(),
      productItems.items().toArray(new PricedProductItem[0]),
      productItems.items().stream()
        .mapToDouble(PricedProductItem::totalPrice)
        .sum()
    );
  }

  private static ProductItems productItemsOf(ShoppingCartSummary summary) {
    return new ProductItems(new ArrayList<>(Arrays.asList(summary.getProductItems())));
  }
}
