package io.eventdriven.introductiontoeventsourcing.v1.productitems;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {}
