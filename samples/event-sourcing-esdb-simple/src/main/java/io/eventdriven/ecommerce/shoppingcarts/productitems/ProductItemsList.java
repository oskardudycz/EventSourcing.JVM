package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ProductItems
  List<PricedProductItem> items
) {
  public ProductItemsList add(PricedProductItem productItem) {
    var clone = new ArrayList<>(items);

    var currentProductItem = find(productItem);

    if (currentProductItem.isEmpty())
      clone.add(productItem);
    else
      clone.set(clone.indexOf(currentProductItem.get()), currentProductItem.get().mergeWith(productItem));

    return new ProductItemsList(clone);
  }

  public ProductItemsList remove(PricedProductItem productItem) {
    var clone = new ArrayList<>(items);

    var currentProductItem = find(productItem);

    if (currentProductItem.isEmpty())
      throw new IllegalStateException("Product item wasn't found");

    clone.remove(currentProductItem.get());

    return new ProductItemsList(clone);
  }

  public Optional<PricedProductItem> find(PricedProductItem productItem) {
    return items.stream().filter(pi -> pi.matchesProductAndUnitPrice(productItem)).findAny();
  }

  public static ProductItemsList empty() {
    return new ProductItemsList(new ArrayList<>());
  }

  @Override
  public String toString() {
    return "ProductItemsList{items=%s}".formatted(items);
  }
}

