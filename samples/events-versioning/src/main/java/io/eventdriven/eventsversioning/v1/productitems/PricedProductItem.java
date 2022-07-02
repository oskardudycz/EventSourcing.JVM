package io.eventdriven.eventsversioning.v1.productitems;

public record PricedProductItem(
  ProductItem productItem,
  double unitPrice
) {}
