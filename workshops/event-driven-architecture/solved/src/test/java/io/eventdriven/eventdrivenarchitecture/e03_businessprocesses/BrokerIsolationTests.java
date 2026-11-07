package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses")
public class BrokerIsolationTests {
  @ArchTest
  static final ArchRule kafkaVariantDoesNotDependOnRabbitMQ =
    noClasses().that().resideInAPackage("..sagas_kafka..")
      .should().dependOnClassesThat().resideInAnyPackage(
        "org.springframework.amqp..",
        "com.rabbitmq..",
        "..sagas_rabbitmq.."
      );

  @ArchTest
  static final ArchRule rabbitMQVariantDoesNotDependOnKafka =
    noClasses().that().resideInAPackage("..sagas_rabbitmq..")
      .should().dependOnClassesThat().resideInAnyPackage(
        "org.apache.kafka..",
        "org.springframework.kafka..",
        "..sagas_kafka.."
      );
}
