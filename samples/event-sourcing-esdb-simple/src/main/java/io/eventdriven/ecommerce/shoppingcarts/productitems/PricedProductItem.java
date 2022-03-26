package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.UUID;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {
  public PricedProductItem {
    if (unitPrice <= 0)
      throw new IllegalArgumentException("Unit Price has to be a positive number");
  }

  public UUID productId() {
    return productItem.productId();
  }

  public int quantity() {
    return productItem.quantity();
  }

  public double totalPrice() {
    return quantity() * unitPrice();
  }

  boolean matchesProductAndUnitPrice(PricedProductItem pricedProductItem) {
    return productId().equals(pricedProductItem.productId()) && unitPrice() == pricedProductItem.unitPrice();
  }

  PricedProductItem mergeWith(PricedProductItem productItem) {
    if (!productId().equals(productItem.productId()))
      throw new IllegalArgumentException("Product ids do not match.");
    if (unitPrice() != productItem.unitPrice())
      throw new IllegalArgumentException("Product unit prices do not match.");

    return new PricedProductItem(
      new ProductItem(productId(), productItem.quantity() + productItem.quantity()),
      unitPrice()
    );
  }
}
