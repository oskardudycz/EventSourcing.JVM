package io.eventdriven.ecommerce.shoppingcarts.currentcart;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

@Entity
public class CustomerShoppingCart implements Versioned {
    private UUID customerId;

    @EmbeddedId
    private ShoppingCart.Id shoppingCartId;

    @Enumerated(EnumType.STRING)
    private ShoppingCart.Status status;

    private long version;

    public CustomerShoppingCart() {}

    public CustomerShoppingCart(UUID customerId, ShoppingCart.Id shoppingCartId, ShoppingCart.Status status) {
        this.customerId = customerId;
        this.shoppingCartId = shoppingCartId;
        this.status = status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public ShoppingCart.Id getShoppingCartId() {
        return shoppingCartId;
    }

    public ShoppingCart.Status getStatus() {
        return status;
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
