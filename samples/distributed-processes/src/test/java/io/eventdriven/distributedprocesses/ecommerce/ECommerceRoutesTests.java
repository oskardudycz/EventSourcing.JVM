package io.eventdriven.distributedprocesses.ecommerce;

import static io.eventdriven.testing.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageRouter;
import io.eventdriven.distributedprocesses.core.messaging.RoutingTarget;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.PaymentTerms;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.CustomerOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrderRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.SilentPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizedPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentSummaryRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.SilentFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.CustomerShoppingCartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.CartRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartSummaryRepository;
import io.eventdriven.testing.RouteRecordingMessageBus;
import io.eventdriven.testing.TestTracing;
import java.time.Duration;
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
    private final RouteRecordingMessageBus messageBus = new RouteRecordingMessageBus();
    private final MessageRouter router;

    public ECommerceRoutesTests() {
        ECommerceConfig.configure(
                new InMemoryEventStore(TestTracing.untraced()),
                messageBus,
                item -> new io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem(
                        item, 10),
                new SilentPaymentGateway(),
                PaymentValidity.of(Duration.ofMinutes(15)),
                PaymentTerms.within(Duration.ofDays(14)),
                new SilentFulfillmentService(),
                item -> true,
                H2.bean(CustomerShoppingCartRepository.class),
                H2.bean(ShoppingCartSummaryRepository.class),
                H2.bean(CartRepository.class),
                H2.bean(PendingOrderRepository.class),
                H2.bean(OrderAwaitingPaymentRepository.class),
                H2.bean(CustomerOrderRepository.class),
                H2.bean(OrderStatusRepository.class),
                H2.bean(PaymentSummaryRepository.class),
                H2.bean(PendingPaymentRepository.class),
                H2.bean(AuthorizedPaymentRepository.class),
                () -> now);
        router = new MessageRouter(messageBus.routes, message -> new RoutingTarget(null, null));
    }

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
                .allSatisfy(message -> assertThat(messageBus.channels)
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
