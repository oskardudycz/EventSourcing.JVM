package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates"
})
public class KafkaSolution1TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaSolution1TestApplication.class, args);
    }
}