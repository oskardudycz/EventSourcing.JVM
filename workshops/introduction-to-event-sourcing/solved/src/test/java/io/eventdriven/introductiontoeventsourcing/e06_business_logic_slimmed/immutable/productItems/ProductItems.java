package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.immutable.productItems;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProductItems {
  Map<String, Integer> values;

  private ProductItems(Map<String, Integer> values) {
    this.values = values;
  }

  public static ProductItems empty() {
    return new ProductItems(new HashMap<>());
  }

  public ProductItems add(PricedProductItem productItem) {
    var newValues = new HashMap<>(values);

    newValues.compute(key((productItem)), (id, currentQuantity) ->
      Optional.ofNullable(currentQuantity).orElse(0) + productItem.quantity
    );

    return new ProductItems(newValues);
  }

  public ProductItems remove(PricedProductItem productItem) {
    var newValues = new HashMap<>(values);

    newValues.compute(key((productItem)), (id, currentQuantity) ->
      Optional.ofNullable(currentQuantity).orElse(0) - productItem.quantity
    );

    return new ProductItems(newValues);
  }

  public boolean hasEnough(PricedProductItem productItem) {
    var currentQuantity = values.getOrDefault(key(productItem), 0);

    return currentQuantity >= productItem.quantity();
  }


  private static String key(PricedProductItem pricedProductItem) {
    return String.format("%s_%s", pricedProductItem.productId, pricedProductItem.unitPrice());
  }

  public record ProductItem(
    UUID productId,
    int quantity
  ) {
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
}
