package io.eventdriven.distributedprocesses.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageQueue;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageTypeMapper;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
public class ECommerceModulesTests {
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
                                new MessageQueue(OrdersConfig.shoppingCartsExternal, ShoppingCartsConfig.external, "#"),
                                OrdersConfig.class),
                        tuple(new MessageQueue(OrdersConfig.internal, OrdersConfig.internal, "#"), OrdersConfig.class),
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
