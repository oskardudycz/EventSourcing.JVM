package io.eventdriven.eventsversioning.shoppingcarts;

import org.junit.jupiter.api.Test;

public class ShoppingCartTests {
  @Test
  void aggregationWorks() {
//    var clientId = UUID.randomUUID();
//    var shoppingCartId = UUID.randomUUID();
//
//    var shoes = new PricedProductItem(
//      new ProductItem(UUID.randomUUID(), 1),
//      100
//    );
//
//    var tShirt = new PricedProductItem(
//      new ProductItem(UUID.randomUUID(), 2),
//      40
//    );
//
//    var events = new ShoppingCartEvent[]
//      {
//        new ShoppingCartOpened(
//          shoppingCartId,
//          clientId
//        ),
//        new ProductItemAddedToShoppingCart(
//          shoppingCartId,
//          shoes
//        ),
//        new ProductItemAddedToShoppingCart(
//          shoppingCartId,
//          tShirt
//        ),
//        new ProductItemRemovedFromShoppingCart(
//          shoppingCartId,
//          shoes
//        ),
//        new ShoppingCartConfirmed(
//          shoppingCartId,
//          LocalDateTime.now()
//        )
//      };
//
//    var shoppingCart = ShoppingCart.empty();
//    for (var event : events) {
//      shoppingCart = ShoppingCart.when(shoppingCart, event);
//    }
//
//    assertTrue(shoppingCart instanceof ConfirmedShoppingCart);
//    assertEquals(shoppingCart.id(), shoppingCartId);
//    assertEquals(shoppingCart.clientId(), clientId);
//    assertTrue(shoppingCart.isClosed());
//    assertEquals(Status.Confirmed, shoppingCart.status());
//
//    assertEquals(shoppingCart.productItems().items().stream().count(), 1);
//
//    var tShirtFromShoppingCart = shoppingCart.productItems().items().get(0);
//    assertEquals(tShirt, tShirtFromShoppingCart);
  }
}
