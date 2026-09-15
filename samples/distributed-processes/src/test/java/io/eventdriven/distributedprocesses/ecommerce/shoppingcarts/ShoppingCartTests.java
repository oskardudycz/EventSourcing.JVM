package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartTests extends AggregateSpecification<ShoppingCart, ShoppingCartEvent, UUID> {
  private final UUID shoppingCartId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final ProductItem productItem = new ProductItem(UUID.randomUUID(), 2);
  private final ProductPriceCalculator priceCalculator = item -> new PricedProductItem(item, 12.5);
  private final OffsetDateTime now = OffsetDateTime.now();

  protected ShoppingCartTests() {
    super(ShoppingCart::empty);
  }

  @Test
  public void openingEmitsShoppingCartOpened() {
    // Given
    given()
      // When
      .when(ignored -> ShoppingCart.open(shoppingCartId, clientId))
      // Then
      .then(new ShoppingCartOpened(shoppingCartId, clientId));
  }

  @Test
  public void addingProductItemEmitsProductItemAddedToShoppingCart() {
    // Given
    given(new ShoppingCartOpened(shoppingCartId, clientId))
      // When
      .when(current -> current.addProductItem(priceCalculator, productItem))
      // Then
      .then(new ProductItemAddedToShoppingCart(
        shoppingCartId,
        new PricedProductItem(productItem, 12.5)
      ));
  }

  @Test
  public void confirmingAlreadyConfirmedCartThrowsAndEnqueuesNothing() {
    // Given
    given(
      new ShoppingCartOpened(shoppingCartId, clientId),
      new ShoppingCartConfirmed(shoppingCartId, now)
    )
      // When
      .when(ShoppingCart::confirm)
      // Then
      .thenThrows(IllegalStateException.class);
  }
}
