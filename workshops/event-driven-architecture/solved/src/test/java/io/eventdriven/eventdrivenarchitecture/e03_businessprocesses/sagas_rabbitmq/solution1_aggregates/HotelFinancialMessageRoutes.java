package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.RabbitMQMessageBusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HotelFinancialMessageRoutes {

    @Bean
    public MessageRoutes hotelFinancialRoutes(RabbitMQMessageBusProperties properties) {
        return new MessageRoutes()
                .route(
                        inPackage(HotelFinancialMessageRoutes.class.getPackageName()),
                        message -> new RoutingTarget(
                                properties.exchange(), message.getClass().getName()));
    }
}
