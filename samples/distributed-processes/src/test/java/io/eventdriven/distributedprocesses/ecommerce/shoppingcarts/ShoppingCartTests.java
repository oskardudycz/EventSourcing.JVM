package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;
import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

public class ShoppingCartTests extends AggregateSpecification<ShoppingCart, ShoppingCartEvent, ShoppingCartId> {
  private final UUID clientId = UUID.randomUUID();
  private final ShoppingCartId shoppingCartId = ShoppingCartId.of(clientId, 1);
  private final ProductItem shoes = new ProductItem(UUID.randomUUID(), 2);
  private final ProductItem shirt = new ProductItem(UUID.randomUUID(), 1);
  private final Map<UUID, Double> unitPrices = new HashMap<>(Map.of(shoes.productId(), 10.0, shirt.productId(), 5.0));
  private final ProductPriceCalculator prices = item -> new PricedProductItem(item, unitPrices.get(item.productId()));
  private final OffsetDateTime now = OffsetDateTime.now();

  protected ShoppingCartTests() {
    super(ShoppingCart::empty);
  }

  @Test
  public void theFirstProductStartsTheCart() {
    given()
      .when(current -> current.addProductItem(shoppingCartId, clientId, prices, shoes, now))
      .then(new ProductItemAddedToShoppingCart(shoppingCartId, clientId, new PricedProductItem(shoes, 10), now));
  }

  @Test
  public void addingAProductThatIsAlreadyInTheCartKeepsTheUnitPriceOfItsLine() {
    unitPrices.put(shoes.productId(), 12.0);

    given(added(shoes, 10))
      .when(current -> current.addProductItem(shoppingCartId, clientId, prices, shoes, now))
      .then(new ProductItemAddedToShoppingCart(shoppingCartId, clientId, new PricedProductItem(shoes, 10), now));
  }

  @Test
  public void removingAProductRecordsItAtTheUnitPriceOfItsLine() {
    var oneShoe = new ProductItem(shoes.productId(), 1);

    given(added(shoes, 10))
      .when(current -> current.removeProductItem(oneShoe, now))
      .then(new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(oneShoe, 10), now));
  }

  @Test
  public void removingMoreThanTheCartHoldsThrows() {
    given(added(shoes, 10))
      .when(current -> current.removeProductItem(new ProductItem(shoes.productId(), 3), now))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void removingAProductThatIsNotInTheCartThrows() {
    given(added(shoes, 10))
      .when(current -> current.removeProductItem(shirt, now))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void checkingOutWithUnchangedPricesChecksTheCartOut() {
    given(added(shoes, 10), added(shirt, 5))
      .when(current -> current.checkOut(prices, now))
      .then(new ShoppingCartCheckedOut(shoppingCartId, now));
  }

  @Test
  public void checkingOutAfterAPriceChangedRecordsTheChangeInsteadOfCheckingOut() {
    unitPrices.put(shoes.productId(), 12.0);

    given(added(shoes, 10), added(shirt, 5))
      .when(current -> current.checkOut(prices, now))
      .then(new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now));
  }

  @Test
  public void checkingOutAgainAtTheNewPriceChecksTheCartOut() {
    unitPrices.put(shoes.productId(), 12.0);

    given(added(shoes, 10), new ProductItemPriceChanged(shoppingCartId, shoes.productId(), 10, 12, now))
      .when(current -> current.checkOut(prices, now))
      .then(new ShoppingCartCheckedOut(shoppingCartId, now));
  }

  @Test
  public void checkingOutAnEmptyCartThrows() {
    given(
      added(shoes, 10),
      new ProductItemRemovedFromShoppingCart(shoppingCartId, new PricedProductItem(shoes, 10), now)
    )
      .when(current -> current.checkOut(prices, now))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void checkingOutACartThatDoesNotExistThrows() {
    given()
      .when(current -> current.checkOut(prices, now))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void checkingOutACheckedOutCartAgainRecordsNothing() {
    given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
      .when(current -> current.checkOut(prices, now))
      .thenNothing();
  }

  @Test
  public void addingToACheckedOutCartThrows() {
    given(added(shoes, 10), new ShoppingCartCheckedOut(shoppingCartId, now))
      .when(current -> current.addProductItem(shoppingCartId, clientId, prices, shirt, now))
      .thenThrows(IllegalStateException.class);
  }

  private ProductItemAddedToShoppingCart added(ProductItem productItem, double unitPrice) {
    return new ProductItemAddedToShoppingCart(shoppingCartId, clientId, new PricedProductItem(productItem, unitPrice), now);
  }
}
