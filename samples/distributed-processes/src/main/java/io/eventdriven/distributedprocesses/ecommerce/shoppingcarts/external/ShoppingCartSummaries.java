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
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartSummaries extends Projection<ShoppingCartSummary> {
  public ShoppingCartSummaries(Database database) {
    super(ShoppingCartSummary.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      ProductItemAddedToShoppingCart.class,
      ProductItemRemovedFromShoppingCart.class,
      ProductItemPriceChanged.class,
      ShoppingCartCheckedOut.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
      case ProductItemRemovedFromShoppingCart e -> e.shoppingCartId();
      case ProductItemPriceChanged e -> e.shoppingCartId();
      case ShoppingCartCheckedOut e -> e.shoppingCartId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable ShoppingCartSummary evolve(@Nullable ShoppingCartSummary current, Object event) {
    return switch (event) {
      case ProductItemAddedToShoppingCart e -> current == null
        ? withProductItems(e.clientId(), ProductItems.empty().add(e.productItem()))
        : withProductItems(current.getClientId(), productItemsOf(current).add(e.productItem()));
      case ProductItemRemovedFromShoppingCart e ->
        withProductItems(current.getClientId(), productItemsOf(current).remove(e.productItem()));
      case ProductItemPriceChanged e ->
        withProductItems(current.getClientId(), productItemsOf(current).changeUnitPrice(e.productId(), e.newUnitPrice()));
      default -> current;
    };
  }

  private static ShoppingCartSummary withProductItems(UUID clientId, ProductItems productItems) {
    return new ShoppingCartSummary(
      clientId,
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
