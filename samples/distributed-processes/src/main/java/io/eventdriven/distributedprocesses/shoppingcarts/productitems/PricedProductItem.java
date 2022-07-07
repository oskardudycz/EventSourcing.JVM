package io.eventdriven.distributedprocesses.shoppingcarts.productitems;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {}
