package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.RabbitMQMessageBusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;

@Configuration
public class HotelFinancialMessageRoutes {

  @Bean
  public MessageRoutes hotelFinancialRoutes(RabbitMQMessageBusProperties properties) {
    return new MessageRoutes()
      .route(
        inPackage(HotelFinancialMessageRoutes.class.getPackageName()),
        message -> new RoutingTarget(properties.exchange(), message.getClass().getName())
      );
  }
}
