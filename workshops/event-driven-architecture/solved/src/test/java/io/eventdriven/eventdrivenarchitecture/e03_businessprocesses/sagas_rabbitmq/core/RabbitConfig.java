package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageMappingRegistry;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "messaging.type", havingValue = "rabbitmq")
public class RabbitConfig {

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter(
        MessageMappingRegistry registry,
        JsonMapper jsonMapper
    ) {
        var converter = new JacksonJsonMessageConverter(jsonMapper);

        var classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(registry.getTypeMappings());

        var trustedPackages = registry.getTrustedPackages();
        if (trustedPackages.isEmpty()) {
            classMapper.setTrustedPackages("*");
        } else {
            classMapper.setTrustedPackages(trustedPackages.toArray(String[]::new));
        }

        converter.setClassMapper(classMapper);
        return converter;
    }


    @Bean
    public TopicExchange hotelFinancialExchange(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue hotelFinancialQueue(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return QueueBuilder.durable(exchangeName + "-saga").build();
    }

    @Bean
    public Binding hotelFinancialBinding(@Value("${app.rabbitmq.exchange:hotel-financial}") String exchangeName) {
        return BindingBuilder.bind(hotelFinancialQueue(exchangeName))
            .to(hotelFinancialExchange(exchangeName))
            .with("#"); // Route all messages to saga queue
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        JacksonJsonMessageConverter rabbitMessageConverter
    ) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        JacksonJsonMessageConverter rabbitMessageConverter
    ) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setObservationEnabled(true);
        return factory;
    }
}