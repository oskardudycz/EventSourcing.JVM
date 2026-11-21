package io.eventdriven.distributedprocesses.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageRouter;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ECommerceRoutesTests {
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OrderId orderId = OrderId.of(UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(orderId.value());
    private final ShipmentId shipmentId = ShipmentId.derivedFrom(orderId.value());
    private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
    private final UUID customerId = UUID.randomUUID();
    private final List<MessageChannel> channels = List.of(
            ShoppingCartsConfig.shoppingCartsExternalChannel(),
            OrdersConfig.ordersInternalChannel(),
            PaymentsConfig.paymentsRequestsChannel(),
            PaymentsConfig.paymentsResponsesChannel(),
            PaymentsConfig.paymentsInternalChannel(),
            ShipmentsConfig.shipmentsRequestsChannel(),
            ShipmentsConfig.shipmentsResponsesChannel(),
            ShipmentsConfig.shipmentsInternalChannel());
    private final MessageRouter router = new MessageRouter(List.of(
            ShoppingCartsConfig.shoppingCartsRoutes(),
            OrdersConfig.ordersRoutes(),
            PaymentsConfig.paymentsRoutes(),
            ShipmentsConfig.shipmentsRoutes()));

    @Test
    public void everyPublishedMessageTypeHasAChannelAndARoutingKey() {
        assertThat(publishedMessages()).allSatisfy(message -> {
            var route = router.routeFor(message);

            assertThat(route.target())
                    .as("channel of %s", message.getClass().getSimpleName())
                    .isNotNull();
            assertThat(route.key())
                    .as("routing key of %s", message.getClass().getSimpleName())
                    .isNotNull();
        });
    }

    @Test
    public void everyChannelAMessageIsRoutedToIsDeclared() {
        assertThat(publishedMessages())
                .allSatisfy(message -> assertThat(channels)
                        .extracting(MessageChannel::name)
                        .as("channel of %s", message.getClass().getSimpleName())
                        .contains(router.routeFor(message).target()));
    }

    @Test
    public void aCommandIsRoutedWithTheRoutingKeyOfItsEntity() {
        var route = router.routeFor(new PaymentCommand.CapturePayment(paymentId));

        assertThat(route.key()).isEqualTo("ecommerce.payment." + Urns.tailOf(paymentId.value()));
    }

    @Test
    public void reserveStockIsRoutedWithTheRoutingKeyOfItsOrder() {
        var route = router.routeFor(new ShipmentCommand.ReserveStock(orderId.value(), new ProductItem[0]));

        assertThat(route.key()).isEqualTo("ecommerce.order." + Urns.tailOf(orderId.value()));
    }

    @Test
    public void thePublishedMessagesListCoversEveryCommandType() {
        var listed = publishedMessages().stream().map(Object::getClass).toList();
        var commandTypes = Stream.of(PaymentCommand.class, ShipmentCommand.class, OrderCommand.class)
                .flatMap(type -> Arrays.stream(type.getPermittedSubclasses()))
                .toList();

        assertThat(listed).containsAll(commandTypes);
    }

    private List<Object> publishedMessages() {
        return PublishedMessages.of(orderId, paymentId, shipmentId, cartId, customerId, now);
    }
}
