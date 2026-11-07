package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RetryBackOff;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageMappingRegistry;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageTransformations;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.*;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy.Decision.RETRY;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.getMessageType;

@Configuration
@ConditionalOnProperty(name = "messaging.type", havingValue = "rabbitmq")
public class RabbitConfig {
    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter(
        MessageMappingRegistry registry,
        JsonMapper jsonMapper,
        // Kids don't try this at home with "*"! See https://github.com/frohoff/ysoserial
        @Value("${app.messaging.trusted-packages:*}") String[] trustedPackages
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
        JacksonJsonMessageConverter rabbitMessageConverter
    ) {
        return new VersionedMessageConverter(jsonMapper, messageTransformations, rabbitMessageConverter);
    }

    @Bean
    public TopicExchange hotelFinancialExchange(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue hotelFinancialQueue(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return QueueBuilder.durable(exchangeName + "-saga")
            .deadLetterExchange(exchangeName + "-dlx")
            .build();
    }

    @Bean
    public Binding hotelFinancialBinding(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return BindingBuilder.bind(hotelFinancialQueue(exchangeName))
            .to(hotelFinancialExchange(exchangeName))
            .with("#"); // Route all messages to saga queue
    }

    @Bean
    public FanoutExchange hotelFinancialDeadLetterExchange(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return new FanoutExchange(exchangeName + "-dlx");
    }

    @Bean
    public Queue hotelFinancialDeadLetterQueue(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return QueueBuilder.durable(exchangeName + "-saga-dlq").build();
    }

    @Bean
    public Binding hotelFinancialDeadLetterBinding(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return BindingBuilder.bind(hotelFinancialDeadLetterQueue(exchangeName))
            .to(hotelFinancialDeadLetterExchange(exchangeName));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        JacksonJsonMessageConverter rabbitMessageConverter
    ) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        template.setObservationEnabled(true);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public MessageFailureStrategy messageFailureStrategy() {
        return MessageFailureStrategy.retryUnlessUnreadable();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageFailureStrategy failureStrategy,
        VersionedMessageConverter messageConverter,
        MeterRegistry meterRegistry,
        @Value("${app.messaging.retry.initial-interval:1s}") Duration initialInterval
    ) {
        var deadLetter = new RejectAndDontRequeueRecoverer();

        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setObservationEnabled(true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
            .configureRetryPolicy(retryPolicy -> retryPolicy
                .backOff(RetryBackOff.exponential(initialInterval))
                .predicate(error -> {
                    var failed = failedMessage(error);
                    return failureStrategy.onFailure(
                        failed.map(MessageMetadata::getMessageType).orElse(null),
                        failed.flatMap(message -> readMessage(messageConverter, message)),
                        error
                    ) == RETRY;
                })
            )
            .recoverer((message, error) -> {
                meterRegistry.counter("messaging.dead_lettered",
                    "message.type", Objects.requireNonNullElse(getMessageType(message), "unknown"),
                    "messaging.destination.name", message.getMessageProperties().getConsumerQueue()
                ).increment();
                deadLetter.recover(message, error);
            })
            .build());
        return factory;
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