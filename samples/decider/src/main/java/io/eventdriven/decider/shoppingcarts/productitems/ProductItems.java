package io.eventdriven.decider.shoppingcarts.productitems;

import java.util.Arrays;
import java.util.stream.Stream;

import static io.eventdriven.decider.core.processing.FunctionalTools.groupingByOrdered;

public record ProductItems(
  PricedProductItem[] values
) {
  public static ProductItems empty() {
    return new ProductItems(new PricedProductItem[]{});
  }

  public ProductItems add(PricedProductItem productItem) {
    return new ProductItems(
      Stream.concat(Arrays.stream(values), Stream.of(productItem))
        .collect(groupingByOrdered(PricedProductItem::productId))
        .entrySet().stream()
        .map(group -> group.getValue().size() == 1 ?
          group.getValue().get(0) :
          new PricedProductItem(
            group.getKey(),
            group.getValue().stream().mapToInt(PricedProductItem::quantity).sum(),
            group.getValue().get(0).unitPrice()
          )
        )
        .toArray(PricedProductItem[]::new)
    );
  }

  public ProductItems remove(PricedProductItem productItem) {
    return new ProductItems(
      Arrays.stream(values())
        .map(pi -> pi.productId().equals(productItem.productId()) ?
          new PricedProductItem(
            pi.productId(),
            pi.quantity() - productItem.quantity(),
            pi.unitPrice()
          )
          : pi
        )
        .filter(pi -> pi.quantity() > 0)
        .toArray(PricedProductItem[]::new)
    );
  }

  public boolean hasEnough(PricedProductItem productItem) {
    var currentQuantity = Arrays.stream(values)
      .filter(pi -> pi.productId().equals(productItem.productId()))
      .mapToInt(PricedProductItem::quantity)
      .sum();

    return currentQuantity >= productItem.quantity();
  }
}
