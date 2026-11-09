package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.KafkaMessageBusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRouter.firstRecordComponent;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;

@Configuration
public class HotelFinancialMessageRoutes {

  @Bean
  public MessageRoutes hotelFinancialRoutes(KafkaMessageBusProperties properties) {
    return new MessageRoutes()
      .route(
        inPackage(HotelFinancialMessageRoutes.class.getPackageName()),
        message -> new RoutingTarget(properties.topic(), firstRecordComponent(message))
      );
  }
}
