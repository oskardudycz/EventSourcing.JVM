package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageTypeMapper;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import java.util.Arrays;
import java.util.List;

public final class TestMessages {
    private TestMessages() {}

    public static List<MessageMapping> moduleMappings() {
        return List.of(
                ShoppingCartsConfig.mapping(),
                OrdersConfig.mapping(),
                PaymentsConfig.mapping(),
                ShipmentsConfig.mapping());
    }

    public static MessageSerializer serializer() {
        return serializer(moduleMappings());
    }

    public static MessageSerializer serializer(MessageMapping... mappings) {
        return serializer(List.of(mappings));
    }

    public static MessageSerializer serializer(List<MessageMapping> mappings) {
        return new MessageSerializer(new MessageTypeMapper(mappings), MessageSerializer.newJsonMapper());
    }

    public static MessageSerializer serializerTrustingPackageOf(Class<?>... classes) {
        return serializer(trustingPackageOf(classes));
    }

    public static MessageMapping trustingPackageOf(Class<?>... classes) {
        return new MessageMapping()
                .trust(Arrays.stream(classes)
                        .map(Class::getPackageName)
                        .distinct()
                        .toArray(String[]::new));
    }
}
