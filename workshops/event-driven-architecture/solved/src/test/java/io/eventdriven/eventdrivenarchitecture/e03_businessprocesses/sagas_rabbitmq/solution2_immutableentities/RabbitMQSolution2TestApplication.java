package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities"
})
public class RabbitMQSolution2TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMQSolution2TestApplication.class, args);
    }
}