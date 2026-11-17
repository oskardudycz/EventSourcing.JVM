package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductItemTests {
  @Test
  public void aProductItemNeedsAProduct() {
    assertThatThrownBy(() -> new ProductItem(null, 1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Product id is required");
  }

  @Test
  public void aProductItemNeedsAPositiveQuantity() {
    assertThatThrownBy(() -> new ProductItem(UUID.randomUUID(), 0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Quantity has to be a positive number");
  }
}
