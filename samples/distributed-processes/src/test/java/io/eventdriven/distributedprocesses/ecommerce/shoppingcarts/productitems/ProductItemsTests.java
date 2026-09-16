package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductItemsTests {
  private final UUID shoes = UUID.randomUUID();
  private final UUID shirt = UUID.randomUUID();

  private static PricedProductItem pricedProductItem(UUID productId, int quantity, double unitPrice) {
    return new PricedProductItem(new ProductItem(productId, quantity), unitPrice);
  }

  @Test
  public void addingTheSameProductTwiceSumsTheQuantities() {
    var productItems = ProductItems.empty()
      .add(pricedProductItem(shoes, 2, 10))
      .add(pricedProductItem(shoes, 3, 10));

    assertThat(productItems.items()).containsExactly(pricedProductItem(shoes, 5, 10));
  }

  @Test
  public void addingADifferentProductKeepsBothLines() {
    var productItems = ProductItems.empty()
      .add(pricedProductItem(shoes, 2, 10))
      .add(pricedProductItem(shirt, 3, 5));

    assertThat(productItems.items())
      .containsExactly(pricedProductItem(shoes, 2, 10), pricedProductItem(shirt, 3, 5));
  }

  @Test
  public void addingTheSameProductAtADifferentUnitPriceKeepsBothLines() {
    var productItems = ProductItems.empty()
      .add(pricedProductItem(shoes, 2, 10))
      .add(pricedProductItem(shoes, 3, 12));

    assertThat(productItems.items())
      .containsExactly(pricedProductItem(shoes, 2, 10), pricedProductItem(shoes, 3, 12));
  }

  @Test
  public void removingPartOfTheQuantityLeavesTheRest() {
    var productItems = ProductItems.empty()
      .add(pricedProductItem(shoes, 5, 10))
      .remove(pricedProductItem(shoes, 2, 10));

    assertThat(productItems.items()).containsExactly(pricedProductItem(shoes, 3, 10));
  }

  @Test
  public void removingTheWholeQuantityDropsTheLine() {
    var productItems = ProductItems.empty()
      .add(pricedProductItem(shoes, 5, 10))
      .remove(pricedProductItem(shoes, 5, 10));

    assertThat(productItems.items()).isEmpty();
  }

  @Test
  public void removingMoreThanWasAddedThrows() {
    var productItems = ProductItems.empty().add(pricedProductItem(shoes, 2, 10));

    assertThatThrownBy(() -> productItems.remove(pricedProductItem(shoes, 3, 10)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Not enough product items");
  }

  @Test
  public void removingAProductThatWasNeverAddedThrows() {
    var productItems = ProductItems.empty().add(pricedProductItem(shoes, 2, 10));

    assertThatThrownBy(() -> productItems.remove(pricedProductItem(shirt, 1, 5)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Product item wasn't found");
  }
}
