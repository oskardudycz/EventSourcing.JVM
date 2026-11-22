package io.eventdriven.ecommerce;

import io.eventdriven.core.configuration.Settings;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.eventstore.kurrentdb.KurrentDBEventStore;
import io.eventdriven.core.eventstore.postgresql.PostgreSQLEventStore;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageFailureStrategy;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.core.messaging.kafka.KafkaMessageBus;
import io.eventdriven.core.messaging.rabbitmq.RabbitMQMessageBus;
import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.messaging.versioning.MessageTypeMapper;
import io.eventdriven.core.processing.RetryBackOff;
import io.eventdriven.core.projections.ProjectionsDatabaseConfig;
import io.eventdriven.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.ecommerce.payments.sandbox.SandboxPaymentGateway;
import io.eventdriven.ecommerce.shipments.sandbox.SandboxFulfillmentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@Import(ProjectionsDatabaseConfig.class)
public class ECommerceApplicationConfig {
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "workers.scheduled", havingValue = "true", matchIfMissing = true)
    static class ScheduledWorkers {}

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("E-Commerce").version("1.0.0"));
    }

    @Bean
    GlobalOperationCustomizer eventCatalogOperationTypes() {
        return (operation, handlerMethod) -> {
            operation.addExtension(
                    "x-eventcatalog-message-type",
                    handlerMethod.hasMethodAnnotation(GetMapping.class) ? "query" : "command");
            return operation;
        };
    }

    @Bean
    InitializingBean openTelemetryLogAppender(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    @Bean
    Supplier<OffsetDateTime> clock() {
        return OffsetDateTime::now;
    }

    @Bean
    MessageContext messageContext(Tracer tracer) {
        return new MessageContext(tracer);
    }

    @Bean
    JsonMapper jsonMapper() {
        return MessageSerializer.newJsonMapper();
    }

    @Bean
    MessageTypeMapper messageTypeMapper(List<MessageMapping> mappings) {
        return new MessageTypeMapper(mappings);
    }

    @Bean
    MessageSerializer messageSerializer(MessageTypeMapper typeMapper, JsonMapper jsonMapper) {
        return new MessageSerializer(typeMapper, jsonMapper);
    }

    @Bean
    EventStore eventStore(
            @Value("${eventstore.type}") String type,
            @Value("${kurrentdb.connectionstring}") String kurrentDBConnectionString,
            @Value("${eventstore.postgresql.url}") String postgreSQLUrl,
            @Value("${eventstore.postgresql.username}") String postgreSQLUsername,
            @Value("${eventstore.postgresql.password}") String postgreSQLPassword,
            MessageContext messageContext,
            MessageSerializer serializer,
            ObjectProvider<ObservationRegistry> observations,
            SubscriptionCheckpoints checkpoints) {
        return switch (type) {
            case "in-memory" -> new InMemoryEventStore(messageContext, serializer);
            case "kurrentdb" ->
                KurrentDBEventStore.connectingTo(
                        kurrentDBConnectionString,
                        messageContext,
                        serializer,
                        observations.getObject(),
                        checkpoints,
                        RetryBackOff.exponential(Duration.ofSeconds(1)));
            case "postgresql" ->
                PostgreSQLEventStore.connectingTo(
                        postgreSQLUrl,
                        postgreSQLUsername,
                        postgreSQLPassword,
                        messageContext,
                        serializer,
                        checkpoints,
                        RetryBackOff.exponential(Duration.ofSeconds(1)),
                        Duration.ofMillis(100));
            default -> throw Settings.unknownValue("eventstore.type", type, "in-memory", "kurrentdb", "postgresql");
        };
    }

    @Bean
    @ConditionalOnProperty(name = "messaging.bus", havingValue = "kafka")
    KafkaTemplate<String, byte[]> deadLetterKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
            @Value("${messaging.kafka.publish-timeout}") Duration kafkaPublishTimeout) {
        var template = new KafkaTemplate<>(KafkaMessageBus.producerFactory(kafkaBootstrapServers, kafkaPublishTimeout));
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    MessageBus messageBus(
            @Value("${messaging.bus}") String type,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
            @Value("${messaging.kafka.publish-timeout}") Duration kafkaPublishTimeout,
            @Value("${messaging.retry.initial-interval}") Duration retryInitialInterval,
            @Value("${spring.rabbitmq.host}") String rabbitMQHost,
            @Value("${spring.rabbitmq.port}") int rabbitMQPort,
            @Value("${spring.rabbitmq.username}") String rabbitMQUsername,
            @Value("${spring.rabbitmq.password}") String rabbitMQPassword,
            @Value("${messaging.rabbitmq.publish-timeout}") Duration rabbitMQPublishTimeout,
            ApplicationContext applicationContext,
            MessageContext messageContext,
            MessageSerializer serializer,
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<MeterRegistry> meters,
            ObjectProvider<KafkaTemplate<String, byte[]>> deadLetterTemplate,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        return switch (type) {
            case "in-memory" -> new InMemoryMessageBus(messageContext, routes, channels, queues);
            case "kafka" ->
                KafkaMessageBus.connectingTo(
                        kafkaBootstrapServers,
                        messageContext,
                        observations.getObject(),
                        serializer,
                        kafkaPublishTimeout,
                        new KafkaMessageBus.FailureHandling(
                                MessageFailureStrategy.retryUnlessUnreadable(),
                                RetryBackOff.exponential(retryInitialInterval),
                                meters.getObject(),
                                deadLetterTemplate.getObject()),
                        routes,
                        channels,
                        queues);
            case "rabbitmq" ->
                RabbitMQMessageBus.connectingTo(
                        rabbitMQHost,
                        rabbitMQPort,
                        rabbitMQUsername,
                        rabbitMQPassword,
                        messageContext,
                        applicationContext,
                        serializer,
                        rabbitMQPublishTimeout,
                        new RabbitMQMessageBus.FailureHandling(
                                MessageFailureStrategy.retryUnlessUnreadable(),
                                RetryBackOff.exponential(retryInitialInterval),
                                meters.getObject()),
                        routes,
                        channels,
                        queues);
            default -> throw Settings.unknownValue("messaging.bus", type, "in-memory", "kafka", "rabbitmq");
        };
    }

    @Bean
    SandboxPaymentGateway sandboxPaymentGateway(MessageBus messageBus, Supplier<OffsetDateTime> clock) {
        return new SandboxPaymentGateway(messageBus, clock);
    }

    @Bean
    SandboxFulfillmentService sandboxFulfillmentService(MessageBus messageBus) {
        return new SandboxFulfillmentService(messageBus);
    }
}
