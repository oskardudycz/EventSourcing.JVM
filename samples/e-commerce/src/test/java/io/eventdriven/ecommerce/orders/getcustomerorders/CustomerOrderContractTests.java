package io.eventdriven.ecommerce.orders.getcustomerorders;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.strictland.MessageContract;
import org.junit.jupiter.api.Test;

public class CustomerOrderContractTests {
    @Test
    void theCustomerOrderResponseBodyKeepsItsFormat() {
        MessageContract.specification(options)
                .given(new CustomerOrder(
                        Order.Id.of(uuid(2)), uuid(1), at, 129.99, Order.Progress.AwaitingPayment, false))
                .whenSerialized()
                .thenContractIsUnchanged();
    }
}
