package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates"
})
public class RabbitMQSolution1TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQSolution1TestApplication.class, args);
    }
}