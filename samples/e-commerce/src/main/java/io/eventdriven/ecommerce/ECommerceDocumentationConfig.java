package io.eventdriven.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.core.documentation.EventCatalogMessageTypes;
import io.eventdriven.core.documentation.InMemoryBindings;
import io.eventdriven.core.documentation.InMemoryPublishingController;
import io.eventdriven.core.documentation.MessageBusClassScanner;
import io.eventdriven.core.documentation.MessageDescriptions;
import io.eventdriven.core.documentation.ModuleGroupingService;
import io.eventdriven.core.documentation.ModuleOperations;
import io.eventdriven.core.documentation.NestedRecordPayloadCreator;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.orders.OrderCommand;
import io.eventdriven.ecommerce.orders.OrderFacade;
import io.eventdriven.ecommerce.orders.OrderSaga;
import io.eventdriven.ecommerce.payments.PaymentCommand;
import io.eventdriven.ecommerce.payments.PaymentFacade;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.ecommerce.shipments.ShipmentCommand;
import io.eventdriven.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEventForwarder;
import io.eventdriven.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.github.springwolf.core.asyncapi.components.ComponentsService;
import io.github.springwolf.core.asyncapi.scanners.beans.BeanMethodsScanner;
import io.github.springwolf.core.asyncapi.scanners.classes.spring.ComponentClassScanner;
import io.github.springwolf.core.controller.PublishingPayloadCreator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ECommerceDocumentationConfig {
    @Bean
    MessageBusClassScanner messageBusClassScanner(
            ComponentClassScanner componentClassScanner, BeanMethodsScanner beanMethodsScanner) {
        return new MessageBusClassScanner(
                componentClassScanner,
                beanMethodsScanner,
                List.of(
                        ShoppingCartExternalEventForwarder.class,
                        OrderFacade.class,
                        OrderSaga.class,
                        PaymentFacade.class,
                        PaymentExternalEventForwarder.class,
                        ShipmentFacade.class,
                        ShipmentExternalEventForwarder.class));
    }

    @Bean
    EventCatalogMessageTypes eventCatalogMessageTypes() {
        return new EventCatalogMessageTypes(List.of(OrderCommand.class, PaymentCommand.class, ShipmentCommand.class));
    }

    @Bean
    ModuleOperations moduleOperations() {
        return new ModuleOperations(
                ECommerceDocumentationConfig.class.getPackageName(), Map.of("shoppingcarts", "shopping_carts"));
    }

    @Bean
    ModuleGroupingService moduleGroupingService() {
        return new ModuleGroupingService();
    }

    @Bean
    MessageDescriptions messageDescriptions() {
        return new MessageDescriptions();
    }

    @Bean
    InMemoryBindings inMemoryBindings() {
        return new InMemoryBindings();
    }

    @Bean
    NestedRecordPayloadCreator nestedRecordPayloadCreator(
            ComponentsService componentsService, ObjectMapper jsonMapper) {
        return new NestedRecordPayloadCreator(componentsService, jsonMapper);
    }

    @Bean
    InMemoryPublishingController inMemoryPublishingController(
            PublishingPayloadCreator publishingPayloadCreator, MessageBus messageBus) {
        return new InMemoryPublishingController(publishingPayloadCreator, messageBus);
    }
}
