package io.eventdriven.ecommerce.shoppingcarts.external;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class ShoppingCartSummary implements Versioned {
    @EmbeddedId
    private ShoppingCart.Id shoppingCartId;

    private UUID customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<PricedProductItem> productItems;

    private double totalPrice;
    private long version;

    public ShoppingCartSummary() {}

    public ShoppingCartSummary(
            ShoppingCart.Id shoppingCartId, UUID customerId, PricedProductItem[] productItems, double totalPrice) {
        this.shoppingCartId = shoppingCartId;
        this.customerId = customerId;
        this.productItems = List.of(productItems);
        this.totalPrice = totalPrice;
    }

    public ShoppingCart.Id getShoppingCartId() {
        return shoppingCartId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public PricedProductItem[] getProductItems() {
        return productItems.toArray(new PricedProductItem[0]);
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }
}
