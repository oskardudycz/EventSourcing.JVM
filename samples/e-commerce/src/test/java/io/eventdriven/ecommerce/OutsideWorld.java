package io.eventdriven.ecommerce;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.ecommerce.payments.PaymentProvider;
import io.eventdriven.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.Warehouse;
import io.eventdriven.ecommerce.shoppingcarts.pricing.ChangeablePrices;
import io.eventdriven.testing.time.MovableClock;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class OutsideWorld {
    @Bean
    @Primary
    MovableClock movableClock() {
        return new MovableClock();
    }

    @Bean
    @Primary
    ChangeablePrices changeablePrices() {
        return new ChangeablePrices();
    }

    @Bean
    @Primary
    PaymentProvider paymentProvider(MessageBus messageBus) {
        return new PaymentProvider(messageBus);
    }

    @Bean
    @Primary
    Warehouse warehouse() {
        return new Warehouse();
    }

    @Bean
    @Primary
    Function<Shipment.ProductItem, Boolean> warehouseStock(Warehouse warehouse) {
        return warehouse::isAvailable;
    }

    @Bean
    Time time(
            MovableClock clock,
            ScheduledPaymentTimeoutWorker paymentTimeoutWorker,
            ScheduledAuthorizationExpiryWorker authorizationExpiryWorker,
            ScheduledUnpaidOrdersWorker unpaidOrdersWorker) {
        return new Time(clock, paymentTimeoutWorker, authorizationExpiryWorker, unpaidOrdersWorker);
    }
}
