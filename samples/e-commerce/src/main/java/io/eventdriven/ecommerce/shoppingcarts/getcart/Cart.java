package io.eventdriven.ecommerce.shoppingcarts.getcart;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Cart implements Versioned {
    public record Line(UUID productId, int quantity, double unitPrice) {}

    @EmbeddedId
    private ShoppingCart.Id shoppingCartId;

    private UUID customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Line> lines;

    private double totalPrice;
    private boolean checkedOut;
    private long version;

    public Cart() {}

    public Cart(
            ShoppingCart.Id shoppingCartId, UUID customerId, List<Line> lines, double totalPrice, boolean checkedOut) {
        this.shoppingCartId = shoppingCartId;
        this.customerId = customerId;
        this.lines = lines;
        this.totalPrice = totalPrice;
        this.checkedOut = checkedOut;
    }

    public ShoppingCart.Id getShoppingCartId() {
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
