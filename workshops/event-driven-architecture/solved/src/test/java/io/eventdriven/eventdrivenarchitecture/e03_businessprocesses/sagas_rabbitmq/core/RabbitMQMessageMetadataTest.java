package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.CAUSATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.CORRELATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RabbitMQSagaTestApplication.class)
@TestPropertySource(properties = {
  "app.rabbitmq.exchange=message-metadata"
})
@AutoConfigureTracing
public class RabbitMQMessageMetadataTest {
  public record RoomInspected(String roomId, String notes) {
  }

  @Autowired
  private RabbitMQMessageBus messageBus;
  @Autowired
  private Tracer tracer;
  @Autowired
  private AmqpAdmin amqpAdmin;
  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange}")
  private String exchange;

  @Test
  public void messageStartingAProcess_CarriesAMessageIdAndAGeneratedCorrelationId() {
    var queue = bindQueue();

    messageBus.publish(List.of(new RoomInspected(UUID.randomUUID().toString(), "clean")));

    var headers = receive(queue).getMessageProperties();
    assertThat(headers.<String>getHeader(MESSAGE_ID)).satisfies(UUID::fromString);
    assertThat(headers.<String>getHeader(CORRELATION_ID)).satisfies(UUID::fromString);
    assertThat(headers.<String>getHeader(CAUSATION_ID)).isNull();
  }

  @Test
  public void messagePublishedWhileHandlingAnother_CarriesItsCorrelationAndCausationIds() {
    var queue = bindQueue();

    try (var _ = tracer.createBaggageInScope(CORRELATION_ID, "process-correlation-id");
         var _ = tracer.createBaggageInScope(CAUSATION_ID, "handled-message-id")) {
      messageBus.publish(List.of(new RoomInspected(UUID.randomUUID().toString(), "clean")));
    }

    var headers = receive(queue).getMessageProperties();
    assertThat(headers.<String>getHeader(MESSAGE_ID)).satisfies(UUID::fromString);
    assertThat(headers.<String>getHeader(CORRELATION_ID)).isEqualTo("process-correlation-id");
    assertThat(headers.<String>getHeader(CAUSATION_ID)).isEqualTo("handled-message-id");
  }

  private String bindQueue() {
    var queue = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(exchange)).with(RoomInspected.class.getName()));
    return queue.getName();
  }

  private Message receive(String queue) {
    var message = rabbitTemplate.receive(queue, 5000);
    assertThat(message).as("message should arrive at " + queue).isNotNull();
    return message;
  }
}
