package io.eventdriven.distributedprocesses.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.TestMessages;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ECommerceMessagesTests {
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final List<Object> messages = PublishedMessages.of(
            orderId,
            PaymentId.derivedFrom(orderId.value()),
            ShipmentId.derivedFrom(orderId.value()),
            ShoppingCartId.of(UUID.randomUUID()),
            UUID.randomUUID(),
            OffsetDateTime.now(ZoneOffset.UTC));
    private final MessageSerializer serializer = TestMessages.serializer(ECommerceMessages.mapping());

    @Test
    public void everyPublishedMessageTypeHasAStableTypeNameThatIsNotItsClassName() {
        assertThat(messages)
                .allSatisfy(message -> assertThat(serializer.typeNameOf(message.getClass()))
                        .as("type name of %s", message.getClass().getSimpleName())
                        .isNotEqualTo(message.getClass().getName()));
    }

    @Test
    public void everyPublishedMessageTypeHasItsOwnTypeName() {
        var typeNames = messages.stream()
                .map(message -> serializer.typeNameOf(message.getClass()))
                .toList();

        assertThat(typeNames).doesNotHaveDuplicates();
    }

    @Test
    public void everyPublishedMessageIsReadBackFromItsStableTypeName() {
        assertThat(messages).allSatisfy(message -> {
            var typeName = serializer.typeNameOf(message.getClass());

            var read = serializer.read(typeName, serializer.write(message));

            assertThat(read).as(typeName).usingRecursiveComparison().isEqualTo(message);
        });
    }
}
