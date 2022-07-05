package io.eventdriven.uniqueness.shoppingcarts.productitems;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {}
