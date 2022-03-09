package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.UUID;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {
  public UUID productId() {
    return productItem.productId();
  }

  public int quantity() {
    return productItem.quantity();
  }

  public double totalPrice() {
    return quantity() * unitPrice();
  }

  public static PricedProductItem From(ProductItem productItem, double unitPrice) {
    if (productItem == null)
      throw new IllegalArgumentException("Product Item cannot be null");

    if (unitPrice <= 0)
      throw new IllegalArgumentException("Unit Price has to be a positive number");

    return new PricedProductItem(productItem, unitPrice);
  }

  public boolean MatchesProductAndUnitPrice(PricedProductItem pricedProductItem) {
    return productId() == pricedProductItem.productId() && unitPrice() == pricedProductItem.unitPrice();
  }

  public PricedProductItem MergeWith(PricedProductItem productItem) {
    if (productId() != productItem.productId())
      throw new IllegalArgumentException("Product ids do not match.");
    if (unitPrice() != productItem.unitPrice())
      throw new IllegalArgumentException("Product unit prices do not match.");

    return new PricedProductItem(
      new ProductItem(productId(), productItem.quantity() + productItem.quantity()),
      unitPrice()
    );
  }
}
