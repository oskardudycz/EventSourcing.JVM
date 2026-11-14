package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ProductItems(
  List<PricedProductItem> items
) {
  public ProductItems add(PricedProductItem productItem) {
    var clone = new ArrayList<>(items);

    var currentProductItem = find(productItem);

    if (currentProductItem.isEmpty())
      clone.add(productItem);
    else
      clone.set(clone.indexOf(currentProductItem.get()), currentProductItem.get().mergeWith(productItem));

    return new ProductItems(clone);
  }

  public ProductItems remove(PricedProductItem productItem) {
    var clone = new ArrayList<>(items);

    var currentProductItem = assertThatCanRemove(productItem);

    clone.remove(currentProductItem);

    return new ProductItems(clone);
  }

  Optional<PricedProductItem> find(PricedProductItem productItem) {
    return items.stream().filter(pi -> pi.matchesProductAndUnitPrice(productItem)).findAny();
  }

  public PricedProductItem assertThatCanRemove(PricedProductItem productItem) {

    var currentProductItem = find(productItem);

    if (currentProductItem.isEmpty())
      throw new IllegalStateException("Product item wasn't found");

    if(currentProductItem.get().quantity() < productItem.quantity())
      throw new IllegalStateException("Not enough product items");

    return currentProductItem.get();
  }

  public static ProductItems empty() {
    return new ProductItems(new ArrayList<>());
  }

  @Override
  public String toString() {
    return "ProductItemsList{items=%s}".formatted(items);
  }
}

