package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import java.util.List;
import java.util.UUID;

public class Cart implements Versioned {
    public record Line(UUID productId, int quantity, double unitPrice) {}

    private ShoppingCartId shoppingCartId;
    private UUID customerId;
    private List<Line> lines;
    private double totalPrice;
    private boolean checkedOut;
    private long version;

    public Cart() {}

    public Cart(
            ShoppingCartId shoppingCartId, UUID customerId, List<Line> lines, double totalPrice, boolean checkedOut) {
        this.shoppingCartId = shoppingCartId;
        this.customerId = customerId;
        this.lines = lines;
        this.totalPrice = totalPrice;
        this.checkedOut = checkedOut;
    }

    public ShoppingCartId getShoppingCartId() {
        return shoppingCartId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<Line> getLines() {
        return lines;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public boolean isCheckedOut() {
        return checkedOut;
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
