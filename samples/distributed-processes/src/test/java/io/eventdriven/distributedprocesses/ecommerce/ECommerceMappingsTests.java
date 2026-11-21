package io.eventdriven.distributedprocesses.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.testing.TestMessages;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ECommerceMappingsTests {
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final List<Object> messages = PublishedMessages.of(
            orderId,
            PaymentId.derivedFrom(orderId.value()),
            ShipmentId.derivedFrom(orderId.value()),
            ShoppingCartId.of(UUID.randomUUID()),
            UUID.randomUUID(),
            OffsetDateTime.now(ZoneOffset.UTC));

    @Test
    public void everyPublishedMessageIsNamedByItsClassAndReadBackByTheModulesTogether() {
        var serializer = TestMessages.serializer();

        assertThat(messages).allSatisfy(message -> {
            var typeName = serializer.typeNameOf(message.getClass());

            var read = serializer.read(typeName, serializer.write(message));

            assertThat(typeName).isEqualTo(message.getClass().getName());
            assertThat(read).as(typeName).usingRecursiveComparison().isEqualTo(message);
        });
    }

    @Test
    public void ordersTrustTheExternalEventsOfPaymentsShipmentsAndCarts() {
        var ordersOnly = TestMessages.serializer(OrdersConfig.mapping());
        var externalEvents = messages.stream()
                .filter(message -> message.getClass().getPackageName().endsWith(".external"))
                .toList();

        assertThat(externalEvents)
                .isNotEmpty()
                .allSatisfy(message -> assertThat(readBack(ordersOnly, message))
                        .usingRecursiveComparison()
                        .isEqualTo(message));
    }

    @Test
    public void eachModuleTrustsItsOwnMessages() {
        var mappings = Map.of(
                "shoppingcarts", ShoppingCartsConfig.mapping(),
                "orders", OrdersConfig.mapping(),
                "payments", PaymentsConfig.mapping(),
                "shipments", ShipmentsConfig.mapping());

        mappings.forEach((module, mapping) -> assertThat(messagesOf(module))
                .as(module)
                .isNotEmpty()
                .allSatisfy(message -> assertThat(readBack(TestMessages.serializer(mapping), message))
                        .usingRecursiveComparison()
                        .isEqualTo(message)));
    }

    private List<Object> messagesOf(String module) {
        return messages.stream()
                .filter(message -> message.getClass().getPackageName().contains("." + module))
                .toList();
    }

    private static Object readBack(MessageSerializer serializer, Object message) {
        return serializer.read(serializer.typeNameOf(message.getClass()), serializer.write(message));
    }
}
