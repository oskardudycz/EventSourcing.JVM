package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ClientShoppingCartsTests {
  private final UUID clientId = UUID.randomUUID();
  private final ShoppingCartId firstCartId = ShoppingCartId.of(clientId, 1);
  private final ShoppingCartId secondCartId = ShoppingCartId.of(clientId, 2);
  private final PricedProductItem shoes = new PricedProductItem(new ProductItem(UUID.randomUUID(), 1), 10);
  private final OffsetDateTime now = OffsetDateTime.now();

  private final ClientShoppingCarts clientShoppingCarts = new ClientShoppingCarts(new Database());

  @Test
  public void aClientWithNoCartHasNoCurrentCartAndZeroCarts() {
    assertThat(clientShoppingCarts.currentOf(clientId)).isEmpty();
    assertThat(clientShoppingCarts.countOf(clientId)).isZero();
  }

  @Test
  public void theFirstProductMakesItsCartTheCurrentOne() {
    handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, clientId, shoes, now));

    assertThat(clientShoppingCarts.currentOf(clientId)).contains(firstCartId);
    assertThat(clientShoppingCarts.countOf(clientId)).isEqualTo(1);
  }

  @Test
  public void aCheckedOutCartIsNoLongerCurrentButStillCounts() {
    handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, clientId, shoes, now));
    handle(firstCartId, 1, new ShoppingCartCheckedOut(firstCartId, now));

    assertThat(clientShoppingCarts.currentOf(clientId)).isEmpty();
    assertThat(clientShoppingCarts.countOf(clientId)).isEqualTo(1);
  }

  @Test
  public void theNextCartBecomesCurrent() {
    handle(firstCartId, 0, new ProductItemAddedToShoppingCart(firstCartId, clientId, shoes, now));
    handle(firstCartId, 1, new ShoppingCartCheckedOut(firstCartId, now));
    handle(secondCartId, 0, new ProductItemAddedToShoppingCart(secondCartId, clientId, shoes, now));

    assertThat(clientShoppingCarts.currentOf(clientId)).contains(secondCartId);
    assertThat(clientShoppingCarts.countOf(clientId)).isEqualTo(2);
  }

  @Test
  public void anotherClientsCartIsNotCounted() {
    var otherClientId = UUID.randomUUID();
    var otherCartId = ShoppingCartId.of(otherClientId, 1);

    handle(otherCartId, 0, new ProductItemAddedToShoppingCart(otherCartId, otherClientId, shoes, now));

    assertThat(clientShoppingCarts.currentOf(clientId)).isEmpty();
    assertThat(clientShoppingCarts.countOf(clientId)).isZero();
  }

  private void handle(ShoppingCartId shoppingCartId, long position, ShoppingCartEvent event) {
    clientShoppingCarts.handle(new EventEnvelope<>(
      event,
      new EventMetadata(ShoppingCartFacade.mapToStreamId(shoppingCartId), position, position)
    ));
  }
}
