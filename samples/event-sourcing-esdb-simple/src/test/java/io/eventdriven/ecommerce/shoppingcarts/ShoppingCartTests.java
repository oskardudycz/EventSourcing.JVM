package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ShoppingCartTests {
  @Test
  void aggregationWorks() {
    var clientId = UUID.randomUUID();
    var shoppingCartId = UUID.randomUUID();

    var shoes = new PricedProductItem(
      new ProductItem(UUID.randomUUID(), 1),
      100
    );

    var tShirt = new PricedProductItem(
      new ProductItem(UUID.randomUUID(), 2),
      40
    );

    var events = new ShoppingCartEvent[]
      {
        new ShoppingCartOpened(
          clientId
        ),
        new ProductItemAddedToShoppingCart(
          shoes
        ),
        new ProductItemAddedToShoppingCart(
          tShirt
        ),
        new ProductItemRemovedFromShoppingCart(
          shoes
        ),
      };

    var shoppingCart = ShoppingCart.empty();
    for (var event : events) {
      shoppingCart = ShoppingCart.evolve(shoppingCart, event);
    }

    assertInstanceOf(ShoppingCart.Pending.class, shoppingCart);
    var confirmedShoppingCart = (ShoppingCart.Pending) shoppingCart;

    assertEquals(1, confirmedShoppingCart.productItems().size());
    assertTrue(confirmedShoppingCart.productItems().has(tShirt.productId()));

    var tShirtQuantityFromShoppingCart =
      confirmedShoppingCart.productItems().get(tShirt.productId());

    assertTrue(tShirtQuantityFromShoppingCart.isPresent());
    assertEquals(tShirt.quantity(), tShirtQuantityFromShoppingCart.get());
  }
}
