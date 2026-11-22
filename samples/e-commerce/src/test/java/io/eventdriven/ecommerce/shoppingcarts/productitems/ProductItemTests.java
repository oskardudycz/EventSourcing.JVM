package io.eventdriven.ecommerce.shoppingcarts.productitems;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

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
