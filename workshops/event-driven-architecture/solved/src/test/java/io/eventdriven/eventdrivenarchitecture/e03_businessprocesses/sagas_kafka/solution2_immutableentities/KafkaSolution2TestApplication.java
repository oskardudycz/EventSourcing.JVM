package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core",
    "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities"
})
public class KafkaSolution2TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaSolution2TestApplication.class, args);
    }
}