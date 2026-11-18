package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderSaga;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEventForwarder;
import io.github.springwolf.core.asyncapi.scanners.beans.BeanMethodsScanner;
import io.github.springwolf.core.asyncapi.scanners.classes.SpringwolfClassScanner;
import io.github.springwolf.core.asyncapi.scanners.classes.spring.ComponentClassScanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// Springwolf finds annotated methods on Spring components and beans. The message bus is our own and
// its subscribers are wired by the modules' Config classes, so this tells Springwolf where they are.
@Component
public class MessageBusClassScanner extends SpringwolfClassScanner {
    private static final List<Class<?>> onTheMessageBus = List.of(
            ShoppingCartExternalEventForwarder.class,
            OrderFacade.class,
            OrderSaga.class,
            PaymentFacade.class,
            PaymentExternalEventForwarder.class,
            ShipmentFacade.class,
            ShipmentExternalEventForwarder.class);

    public MessageBusClassScanner(ComponentClassScanner componentClassScanner, BeanMethodsScanner beanMethodsScanner) {
        super(componentClassScanner, beanMethodsScanner);
    }

    @Override
    public Set<Class<?>> scan() {
        var classes = new HashSet<>(super.scan());
        classes.addAll(onTheMessageBus);
        return classes;
    }
}
