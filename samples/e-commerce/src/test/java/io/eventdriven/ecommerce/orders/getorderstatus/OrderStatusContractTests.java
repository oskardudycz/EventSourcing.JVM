package io.eventdriven.ecommerce.orders.getorderstatus;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.SnapshotVariant;
import org.junit.jupiter.api.Test;

public class OrderStatusContractTests {
    @Test
    void theOrderStatusResponseBodyKeepsItsFormat() {
        MessageContract.specification(options)
                .given(placedOrder().sent("DHL", "TRACK-1"))
                .whenSerialized()
                .thenContractIsUnchanged();
    }

    @Test
    void theOrderStatusResponseBodyKeepsItsFormatWithOnlyTheRequiredData() {
        MessageContract.specification(options)
                .given(placedOrder())
                .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
                .thenContractIsUnchanged();
    }

    private static OrderStatus placedOrder() {
        return new OrderStatus(
                Order.Id.of(uuid(2)),
                uuid(1),
                new Order.PricedProductItem[] {new Order.PricedProductItem(uuid(5), 2, 64.995)},
                129.99,
                at,
                Payment.Id.of(uuid(3)));
    }
}
