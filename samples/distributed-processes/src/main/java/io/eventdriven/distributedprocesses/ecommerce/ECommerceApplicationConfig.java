package io.eventdriven.distributedprocesses.ecommerce;

import io.eventdriven.distributedprocesses.Application;
import io.eventdriven.distributedprocesses.core.configuration.Settings;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.eventstore.kurrentdb.KurrentDBEventStore;
import io.eventdriven.distributedprocesses.core.eventstore.postgresql.PostgreSQLEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageFailureStrategy;
import io.eventdriven.distributedprocesses.core.messaging.MessageQueue;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RabbitMQMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.distributedprocesses.ecommerce.payments.sandbox.SandboxPaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.shipments.sandbox.SandboxFulfillmentService;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;

@Configuration
@EnableScheduling
public class ECommerceApplicationConfig {
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
    EventStore eventStore(
            @Value("${eventstore.type}") String type,
            @Value("${kurrentdb.connectionstring}") String kurrentDBConnectionString,
            @Value("${eventstore.postgresql.url}") String postgreSQLUrl,
            @Value("${eventstore.postgresql.username}") String postgreSQLUsername,
            @Value("${eventstore.postgresql.password}") String postgreSQLPassword,
            MessageContext messageContext,
            ObjectProvider<ObservationRegistry> observations,
            SubscriptionCheckpoints checkpoints) {
        return switch (type) {
            case "in-memory" -> new InMemoryEventStore(messageContext);
            case "kurrentdb" ->
                KurrentDBEventStore.connectingTo(
                        kurrentDBConnectionString,
                        messageContext,
                        observations.getObject(),
                        checkpoints,
                        RetryBackOff.exponential(Duration.ofSeconds(1)));
            case "postgresql" ->
                PostgreSQLEventStore.connectingTo(
                        postgreSQLUrl,
                        postgreSQLUsername,
                        postgreSQLPassword,
                        messageContext,
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
                        new MessageSerializer(
                                ECommerceMessages.mapping(),
                                EventSerializer.mapper,
                                Application.class.getPackageName()),
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
                        new MessageSerializer(
                                ECommerceMessages.mapping(),
                                EventSerializer.mapper,
                                Application.class.getPackageName()),
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
