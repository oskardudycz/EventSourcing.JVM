package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public
class ECommerceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ECommerceApplication.class, args);
  }
}
