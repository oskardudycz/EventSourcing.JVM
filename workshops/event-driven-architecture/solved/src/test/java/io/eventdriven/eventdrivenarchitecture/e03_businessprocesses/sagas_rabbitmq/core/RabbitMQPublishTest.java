package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.TestApplicationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = TestApplicationConfig.class)
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core"
})
@TestPropertySource(properties = {
  "messaging.type=rabbitmq",
  "app.rabbitmq.exchange=publishing",
  "app.rabbitmq.publish-timeout=5s"
})
@Import(RabbitMQTestConfiguration.class)
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
public class RabbitMQPublishTest {
  public record RoomInspected(String id) {
  }

  private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(5);

  @Autowired
  private RabbitMQMessageBus messageBus;
  @Autowired
  private AmqpAdmin amqpAdmin;
  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange}")
  private String exchange;

  @Test
  public void messages_AreRoutedByTheirTypeName() {
    var queue = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(exchange)).with(RoomInspected.class.getName()));

    var message = new RoomInspected(UUID.randomUUID().toString());
    messageBus.publish(List.of(message));

    assertThat(rabbitTemplate.receiveAndConvert(queue.getName(), 5000)).isEqualTo(message);
  }

  @Test
  public void publishingToAnExchangeThatDoesNotExist_Fails(CapturedOutput output) {
    amqpAdmin.deleteExchange(exchange);

    try {
      var startedAt = System.nanoTime();

      assertThatThrownBy(() -> messageBus.publish(List.of(new RoomInspected(UUID.randomUUID().toString()))))
        .isInstanceOf(AmqpException.class)
        .hasMessageContaining("NOT_FOUND - no exchange '" + exchange + "'");

      assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PUBLISH_TIMEOUT);
      await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(output).contains("Shutdown Signal: channel error; protocol method: #method<channel.close>(reply-code=404")
      );
    } finally {
      amqpAdmin.declareExchange(new TopicExchange(exchange));
      amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(exchange + "-saga")).to(new TopicExchange(exchange)).with("#"));
    }
  }
}
