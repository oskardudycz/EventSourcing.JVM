package io.eventdriven.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRouter;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.messaging.versioning.MessageTypeMapper;
import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.orders.OrderCommand;
import io.eventdriven.ecommerce.orders.OrdersConfig;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentCommand;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import io.eventdriven.ecommerce.payments.PaymentsConfig;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.testing.messaging.TestMessages;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
public class ECommerceMessagingTests {
    @Nested
    public class TypeMappings {
        private final Order.Id orderId = Order.Id.of(UUID.randomUUID());
        private final List<Object> messages = oneOfEachMessage(
                orderId,
                Payment.Id.derivedFrom(orderId.value()),
                Shipment.Id.derivedFrom(orderId.value()),
                ShoppingCart.Id.of(UUID.randomUUID()),
                UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC));

        @Test
        public void everyPublishedMessageIsNamedByItsClassAndReadBackByTheModulesTogether() {
            var serializer = ECommerceMessages.serializer();

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

    @Nested
    public class ModuleDeclarations {
        @Autowired
        private ConfigurableApplicationContext context;

        @Test
        public void eachModuleDeclaresItsOwnChannelsAndQueues() {
            assertThat(declarationsOf(MessageChannel.class))
                    .extracting(declaration -> ((MessageChannel) declaration.bean()).name(), Declaration::owner)
                    .containsExactlyInAnyOrder(
                            tuple(ShoppingCartsConfig.external, ShoppingCartsConfig.class),
                            tuple(OrdersConfig.internal, OrdersConfig.class),
                            tuple(PaymentsConfig.requests, PaymentsConfig.class),
                            tuple(PaymentsConfig.responses, PaymentsConfig.class),
                            tuple(PaymentsConfig.internal, PaymentsConfig.class),
                            tuple(ShipmentsConfig.requests, ShipmentsConfig.class),
                            tuple(ShipmentsConfig.responses, ShipmentsConfig.class),
                            tuple(ShipmentsConfig.internal, ShipmentsConfig.class));

            assertThat(declarationsOf(MessageQueue.class))
                    .extracting(declaration -> (MessageQueue) declaration.bean(), Declaration::owner)
                    .containsExactlyInAnyOrder(
                            tuple(
                                    new MessageQueue(
                                            OrdersConfig.shoppingCartsExternal, ShoppingCartsConfig.external, "#"),
                                    OrdersConfig.class),
                            tuple(
                                    new MessageQueue(OrdersConfig.internal, OrdersConfig.internal, "#"),
                                    OrdersConfig.class),
                            tuple(
                                    new MessageQueue(
                                            OrdersConfig.paymentsResponses,
                                            PaymentsConfig.responses,
                                            OrdersConfig.responsesForOrders),
                                    OrdersConfig.class),
                            tuple(
                                    new MessageQueue(
                                            OrdersConfig.shipmentsResponses,
                                            ShipmentsConfig.responses,
                                            OrdersConfig.responsesForOrders),
                                    OrdersConfig.class),
                            tuple(
                                    new MessageQueue(PaymentsConfig.requests, PaymentsConfig.requests, "#"),
                                    PaymentsConfig.class),
                            tuple(
                                    new MessageQueue(PaymentsConfig.internal, PaymentsConfig.internal, "#"),
                                    PaymentsConfig.class),
                            tuple(
                                    new MessageQueue(ShipmentsConfig.requests, ShipmentsConfig.requests, "#"),
                                    ShipmentsConfig.class),
                            tuple(
                                    new MessageQueue(ShipmentsConfig.internal, ShipmentsConfig.internal, "#"),
                                    ShipmentsConfig.class));
        }

        @Test
        public void eachModuleDeclaresItsOwnRoutes() {
            assertThat(declarationsOf(MessageRoutes.class))
                    .extracting(Declaration::owner)
                    .containsExactlyInAnyOrder(
                            ShoppingCartsConfig.class, OrdersConfig.class, PaymentsConfig.class, ShipmentsConfig.class);
        }

        @Test
        public void eachModuleDeclaresItsOwnTypeMapping() {
            assertThat(declarationsOf(MessageMapping.class))
                    .extracting(Declaration::owner)
                    .containsExactlyInAnyOrder(
                            ShoppingCartsConfig.class, OrdersConfig.class, PaymentsConfig.class, ShipmentsConfig.class);
        }

        @Test
        public void theTypeMapperIsBuiltFromTheMappingsOfTheModules() {
            var typeMapper = context.getBean(MessageTypeMapper.class);

            assertThat(List.of(
                            ShoppingCartExternalEvent.ShoppingCartCheckedOut.class,
                            OrderCommand.PlaceOrder.class,
                            PaymentCommand.CapturePayment.class,
                            ShipmentCommand.ReserveStock.class))
                    .allSatisfy(messageClass -> assertThat(typeMapper.classOf(typeMapper.nameOf(messageClass)))
                            .contains(messageClass));
        }

        private record Declaration(Object bean, Class<?> owner) {}

        private List<Declaration> declarationsOf(Class<?> type) {
            Map<String, ?> beans = context.getBeansOfType(type);

            return beans.entrySet().stream()
                    .map(bean -> new Declaration(bean.getValue(), ownerOf(bean.getKey())))
                    .toList();
        }

        private Class<?> ownerOf(String beanName) {
            var definition = (AnnotatedBeanDefinition) context.getBeanFactory().getBeanDefinition(beanName);

            try {
                return Class.forName(definition.getFactoryMethodMetadata().getDeclaringClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    public class Routes {
        private final OffsetDateTime now = OffsetDateTime.now();
        private final Order.Id orderId = Order.Id.of(UUID.randomUUID());
        private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());
        private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(orderId.value());
        private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
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
            var route = router.routeFor(new ShipmentCommand.ReserveStock(orderId.value(), new Shipment.ProductItem[0]));

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
            return oneOfEachMessage(orderId, paymentId, shipmentId, cartId, customerId, now);
        }
    }

    static List<Object> oneOfEachMessage(
            Order.Id orderId,
            Payment.Id paymentId,
            Shipment.Id shipmentId,
            ShoppingCart.Id cartId,
            UUID customerId,
            OffsetDateTime now) {
        var orderItems = new Order.PricedProductItem[0];
        var cartItems = new io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem[0];

        return List.of(
                new PaymentCommand.AuthorizePayment(paymentId, orderId.value(), 10),
                new PaymentCommand.RecordCheckoutSession(
                        paymentId, new PaymentGateway.CheckoutSession(URI.create("http://localhost/checkout"), now)),
                new PaymentCommand.ConfirmPaymentAuthorization(paymentId, now),
                new PaymentCommand.CapturePayment(paymentId),
                new PaymentCommand.ConfirmPaymentCapture(paymentId),
                new PaymentCommand.FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError),
                new PaymentCommand.CancelPayment(paymentId),
                new PaymentCommand.TimeOutPayment(paymentId, now),
                new PaymentCommand.ExpirePaymentAuthorization(paymentId, now),
                new ShipmentCommand.ReserveStock(orderId.value(), new Shipment.ProductItem[0]),
                new ShipmentCommand.RequestFulfillment(shipmentId),
                new ShipmentCommand.ConfirmPackageSent(shipmentId, "DHL", "JD01"),
                new ShipmentCommand.RejectFulfillment(shipmentId),
                new ShipmentCommand.DeliverPackage(shipmentId),
                new ShipmentCommand.ReleaseStock(shipmentId),
                new OrderCommand.PlaceOrder(orderId, cartId, customerId, orderItems, 10),
                new OrderCommand.RecordOrderPaymentAuthorization(orderId, paymentId, now),
                new OrderCommand.RecordOrderStockReservation(orderId, shipmentId, now),
                new OrderCommand.RecordOrderPackageSent(orderId, "DHL", "JD01", now),
                new OrderCommand.RecordOrderPaymentCapture(orderId, paymentId, now),
                new OrderCommand.RecordOrderPaymentFailure(orderId, paymentId, now),
                new OrderCommand.RecordOrderShipmentFailure(orderId, Order.CancellationReason.Requested, now),
                new OrderCommand.RetryOrderPayment(orderId),
                new OrderCommand.CloseUnpaidOrder(orderId),
                new OrderCommand.CancelOrder(orderId, Order.CancellationReason.Requested),
                new PaymentExternalEvent.PaymentAuthorized(orderId.value(), paymentId, now),
                new PaymentExternalEvent.PaymentCaptured(orderId.value(), paymentId, now),
                new PaymentExternalEvent.PaymentFailed(orderId.value(), paymentId, now),
                new ShipmentExternalEvent.StockReserved(shipmentId, orderId.value(), now),
                new ShipmentExternalEvent.PackageWasSent(shipmentId, orderId.value(), "DHL", "JD01", now),
                new ShipmentExternalEvent.ShipmentFailed(
                        shipmentId,
                        orderId.value(),
                        ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock,
                        now),
                new ShoppingCartExternalEvent.ShoppingCartCheckedOut(cartId, customerId, cartItems, 10, now));
    }
}
