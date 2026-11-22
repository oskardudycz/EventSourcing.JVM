package io.eventdriven.ecommerce;

import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.ecommerce.orders.OrdersConfig;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.testing.messaging.TestMessages;
import java.util.List;

public final class ECommerceMessages {
    private ECommerceMessages() {}

    public static List<MessageMapping> mappings() {
        return List.of(
                ShoppingCartsConfig.mapping(),
                OrdersConfig.mapping(),
                PaymentsConfig.mapping(),
                ShipmentsConfig.mapping());
    }

    public static MessageSerializer serializer() {
        return TestMessages.serializer(mappings());
    }
}
