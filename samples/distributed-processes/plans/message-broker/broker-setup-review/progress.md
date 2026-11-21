# Broker setup review: progress

## Done

- Read the workshop build (`build.gradle`), `application.properties`, `docker-compose.yml` and `observability/tempo-config.yaml`.
- Read the shared core in `e03_businessprocesses/core` (MessageBus, MessageRouter, MessageRoutes, RoutingTarget, MessageFailureStrategy, RetryBackOff, RecordingFailureStrategy).
- Read the Kafka core: KafkaMessageBus, KafkaConfiguration, KafkaMessageBaggageHandler, VersionedMessageConverter, versioning, MessageMetadata, KafkaErrorHandlingTest, KafkaPublishFailureTest, KafkaTestConfiguration.
- Read the RabbitMQ core: RabbitMQMessageBus, RabbitConfig, RabbitMessageBaggageHandler, VersionedMessageConverter, versioning, MessageMetadata, RabbitMQErrorHandlingTest, RabbitMQPublishTest, RabbitMQTestConfiguration.
- Read the e03 README (failure handling, observability).
- Resolved library versions from the Gradle cache: Spring Kafka 4.1.2, kafka-clients 4.2.2, Spring AMQP 4.1.2, Micrometer Tracing OTel bridge 1.7.2.
- Checked in the Spring Kafka 4.1.2 jar (javap): `DeadLetterPublishingRecoverer` default suffix is `-dlt`; `KafkaTemplate` looks up the `ObservationRegistry` only in `afterSingletonsInstantiated()`, so a template built with `new` keeps `ObservationRegistry.NOOP`; `KafkaRecordSenderContext` removes a header before it writes a propagated one.
- Checked in the Micrometer Tracing OTel bridge 1.7.2 jar: `BaggageTextMapPropagator` writes each remote baggage field as its own header.

- Read the target sample bus (`MessageBus`, `InMemoryMessageBus`) and the README sections "Channels and queues" and "Delivering messages reliably".
- Checked the official docs: Spring Kafka (error handling, serdes, containers, sending, observation, dynamic containers), Spring AMQP (template confirms, resilience, message converters, broker configuration, observation, endpoint registration), Spring Boot tracing, Kafka producer/consumer/broker/topic configs, RabbitMQ DLX, quorum queues, semantics, alarms, topic tutorial, Spring Cloud Stream Kafka and RabbitMQ binders.
- Read Spring Boot 4.1.2 property defaults from `META-INF/spring-configuration-metadata.json` in the Boot jars.
- Checked in jars: `MessageProperties.DEFAULT_DELIVERY_MODE` is `PERSISTENT`, default prefetch is 250, `RabbitTemplate` makes a `null` routing key safe, `RabbitAdmin.setRedeclareManualDeclarations` and `RepublishMessageRecovererWithConfirms` exist, `QueueBuilder.quorum()` exists.

## Left

- The harness refused to write `findings.md` from this subagent ("Subagents should return findings as text"). The full findings are in the report to the caller, who can save them as `findings.md` here.
