package io.eventdriven.ecommerce.shoppingcarts.getcart;

import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.strictland.MessageContract;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CartContractTests {
    @Test
    void theCartResponseBodyKeepsItsFormat() {
        var customerId = uuid(1);

        MessageContract.specification(options)
                .given(new Cart(
                        ShoppingCart.Id.of(customerId, 1),
                        customerId,
                        List.of(new Cart.Line(uuid(2), 2, 64.995)),
                        129.99,
                        false))
                .whenSerialized()
                .thenContractIsUnchanged();
    }
}
