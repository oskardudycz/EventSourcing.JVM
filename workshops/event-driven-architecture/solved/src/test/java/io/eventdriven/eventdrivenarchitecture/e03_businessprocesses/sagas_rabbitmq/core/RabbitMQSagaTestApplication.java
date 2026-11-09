package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
@Import(RabbitMQTestConfiguration.class)
public class RabbitMQSagaTestApplication {
}
