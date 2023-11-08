package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class ProductItems {
  private final HashMap<UUID, Integer> items;

  private ProductItems(HashMap<UUID, Integer> items) {
    this.items = items;
  }

  public int size() {
    return items.size();
  }

  public ProductItems with(PricedProductItem productItem) {
    var clone = new HashMap<>(items);
    clone.merge(
      productItem.productId(),
      productItem.quantity(),
      Integer::sum
    );

    return new ProductItems(clone);
  }

  public ProductItems without(PricedProductItem productItem) {
    var clone = new HashMap<>(items);

    clone.merge(
      productItem.productId(),
      -productItem.quantity(),
      Integer::sum
    );

    return new ProductItems(clone);
  }

  public boolean canRemove(PricedProductItem productItem) {

    var currentCount = items.getOrDefault(productItem.productId(), 0);

    return currentCount - productItem.quantity() >= 0;
  }

  public boolean has(UUID productId) {
    return items.containsKey(productId);
  }

  public Optional<Integer> get(UUID productId) {
    return items.containsKey(productId) ?
      Optional.of(items.get(productId))
      : Optional.empty();
  }

  public static ProductItems empty() {
    return new ProductItems(new HashMap<>());
  }

  @Override
  public String toString() {
    return "ProductItemsList{items=%s}".formatted(items);
  }
}

