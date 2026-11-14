package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartConfirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
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
          shoppingCartId,
          clientId
        ),
        new ProductItemAddedToShoppingCart(
          shoppingCartId,
          shoes
        ),
        new ProductItemAddedToShoppingCart(
          shoppingCartId,
          tShirt
        ),
        new ProductItemRemovedFromShoppingCart(
          shoppingCartId,
          shoes
        ),
        new ShoppingCartConfirmed(
          shoppingCartId,
          OffsetDateTime.now()
        )
      };

    ShoppingCart shoppingCart = ShoppingCart.empty();
    for (var event : events) {
      shoppingCart.when(event);
    }
    assertEquals(shoppingCart.id(), shoppingCartId);
    assertEquals(shoppingCart.clientId(), clientId);
    assertEquals(ShoppingCartStatus.Confirmed, shoppingCart.status());

    assertEquals((long) shoppingCart.productItems().items().size(), 1);

    var tShirtFromShoppingCart = shoppingCart.productItems().items().get(0);
    assertEquals(tShirt, tShirtFromShoppingCart);
  }
}
