package io.eventdriven.ecommerce.shoppingcarts.addingproductitem;

import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public record AddProductItemToShoppingCart(
  UUID shoppingCartId,
  ProductItem productItem,
  Long expectedVersion
){}
