package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageMappingRegistry;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageTransformations;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.*;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.Optional;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy.Decision.RETRY;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.getMessageType;

@Configuration
@EnableConfigurationProperties(RabbitMQMessageBusProperties.class)
public class RabbitConfig {
    // Not a MessageConverter bean: Boot would also set it on the listener factory,
    // which then converts every message before VersionedMessageConverter can read it.
    @Bean
    public RabbitTemplateCustomizer jsonMessages(
        MessageMappingRegistry registry,
        JsonMapper jsonMapper,
        // Kids don't try this at home with "*"! See https://github.com/frohoff/ysoserial
        @Value("${app.messaging.trusted-packages:*}") String[] trustedPackages
    ) {
        var converter = rabbitMessageConverter(registry, jsonMapper, trustedPackages);
        return template -> template.setMessageConverter(converter);
    }

    public JacksonJsonMessageConverter rabbitMessageConverter(
        MessageMappingRegistry registry,
        JsonMapper jsonMapper,
        String[] trustedPackages
    ) {
        var converter = new JacksonJsonMessageConverter(jsonMapper);

        var classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(registry.getTypeMappings());
        classMapper.setTrustedPackages(trustedPackages);

        converter.setClassMapper(classMapper);
        return converter;
    }


    @Bean
    public VersionedMessageConverter versionedMessageConverter(
        JsonMapper jsonMapper,
        MessageTransformations messageTransformations,
        RabbitTemplate rabbitTemplate
    ) {
        return new VersionedMessageConverter(jsonMapper, messageTransformations, rabbitTemplate.getMessageConverter());
    }

    @Bean
    public TopicExchange hotelFinancialExchange(RabbitMQMessageBusProperties properties) {
        return new TopicExchange(properties.exchange());
    }

    @Bean
    public Queue hotelFinancialQueue(RabbitMQMessageBusProperties properties) {
        return QueueBuilder.durable(properties.exchange() + "-saga")
            .deadLetterExchange(properties.exchange() + "-dlx")
            .build();
    }

    @Bean
    public Binding hotelFinancialBinding(RabbitMQMessageBusProperties properties) {
        return BindingBuilder.bind(hotelFinancialQueue(properties))
            .to(hotelFinancialExchange(properties))
            .with("#"); // Route all messages to saga queue
    }

    @Bean
    public FanoutExchange hotelFinancialDeadLetterExchange(RabbitMQMessageBusProperties properties) {
        return new FanoutExchange(properties.exchange() + "-dlx");
    }

    @Bean
    public Queue hotelFinancialDeadLetterQueue(RabbitMQMessageBusProperties properties) {
        return QueueBuilder.durable(properties.exchange() + "-saga-dlq").build();
    }

    @Bean
    public Binding hotelFinancialDeadLetterBinding(RabbitMQMessageBusProperties properties) {
        return BindingBuilder.bind(hotelFinancialDeadLetterQueue(properties))
            .to(hotelFinancialDeadLetterExchange(properties));
    }

    @Bean
    public MessageFailureStrategy messageFailureStrategy() {
        return MessageFailureStrategy.retryUnlessUnreadable();
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer retryUnlessTheStrategyDeadLetters(
        MessageFailureStrategy failureStrategy,
        VersionedMessageConverter messageConverter
    ) {
        return settings -> {
            settings.setJitter(settings.getDelay().dividedBy(2));
            settings.setExceptionPredicate(error -> {
                var failed = failedMessage(error);
                return failureStrategy.onFailure(
                    failed.map(MessageMetadata::getMessageType).orElse(null),
                    failed.flatMap(message -> readMessage(messageConverter, message)),
                    error
                ) == RETRY;
            });
        };
    }

    @Bean
    public MessageRecoverer deadLetterRecoverer(MeterRegistry meterRegistry) {
        var deadLetter = new RejectAndDontRequeueRecoverer();

        return (message, error) -> {
            meterRegistry.counter("messaging.dead_lettered",
                "message.type", Objects.requireNonNullElse(getMessageType(message), "unknown"),
                "messaging.destination.name", message.getMessageProperties().getConsumerQueue()
            ).increment();
            deadLetter.recover(message, error);
        };
    }

    private static Optional<Message> failedMessage(Throwable error) {
        return error instanceof ListenerExecutionFailedException failed
            ? Optional.ofNullable(failed.getFailedMessage())
            : Optional.empty();
    }

    private static Optional<Object> readMessage(VersionedMessageConverter messageConverter, Message message) {
        try {
            return Optional.of(messageConverter.read(message));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }
}