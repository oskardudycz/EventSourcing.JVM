package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RabbitMQSagaTestApplication.class)
@TestPropertySource(properties = {
  "app.rabbitmq.exchange=message-routing"
})
@Import(RabbitMQMessageRoutingTest.Routes.class)
@AutoConfigureTracing
public class RabbitMQMessageRoutingTest {
  private static final String ROOMS_EXCHANGE = "message-routing-rooms";
  private static final String GUEST_STAYS_EXCHANGE = "message-routing-guest-stays";

  public record GuestMoved(String hotelId, String guestId) {
  }

  public record HousekeepingRequested(String hotelId, String roomId) {
  }

  public record RoomRepaired(String roomId, String hotelId) {
  }

  @TestConfiguration
  static class Routes {
    @Bean
    MessageRoutes hotelRoutes() {
      return new MessageRoutes()
        .routeKey(GuestMoved.class, message -> "guests." + message.guestId())
        .route(HousekeepingRequested.class, message -> new RoutingTarget(ROOMS_EXCHANGE, "rooms." + message.roomId()))
        .routeTarget(RoomRepaired.class, message -> ROOMS_EXCHANGE)
        .routeTarget(inPackage(GuestCheckedOut.class.getPackageName()), GUEST_STAYS_EXCHANGE);
    }

    @Bean
    TopicExchange guestStaysExchange() {
      return new TopicExchange(GUEST_STAYS_EXCHANGE);
    }

    @Bean
    TopicExchange roomsExchange() {
      return new TopicExchange(ROOMS_EXCHANGE);
    }
  }

  @Autowired
  private RabbitMQMessageBus messageBus;
  @Autowired
  private AmqpAdmin amqpAdmin;
  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange}")
  private String exchange;

  @Test
  public void message_IsSentWithTheRegisteredRoutingKey() {
    var message = new GuestMoved(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var queue = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(exchange)).with("guests." + message.guestId()));

    messageBus.publish(List.of(message));

    assertThat(rabbitTemplate.receiveAndConvert(queue.getName(), 5000)).isEqualTo(message);
  }

  @Test
  public void message_IsSentToTheRegisteredExchangeWithTheRegisteredRoutingKey() {
    var message = new HousekeepingRequested(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var queue = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(ROOMS_EXCHANGE)).with("rooms." + message.roomId()));

    messageBus.publish(List.of(message));

    assertThat(rabbitTemplate.receiveAndConvert(queue.getName(), 5000)).isEqualTo(message);
  }

  @Test
  public void message_IsSentToTheRegisteredExchangeRoutedByItsTypeName() {
    var message = new RoomRepaired(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var queue = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(ROOMS_EXCHANGE)).with(RoomRepaired.class.getName()));

    messageBus.publish(List.of(message));

    assertThat(rabbitTemplate.receiveAndConvert(queue.getName(), 5000)).isEqualTo(message);
  }

  @Test
  public void messagesFromAPackage_AreSentToItsRegisteredExchangeRoutedByTheirTypeNames() {
    var guestStayId = UUID.randomUUID();
    var now = OffsetDateTime.now();
    var command = new CheckOutGuest(guestStayId, now, UUID.randomUUID());
    var event = new GuestCheckedOut(guestStayId, now, UUID.randomUUID());
    var commands = amqpAdmin.declareQueue();
    var events = amqpAdmin.declareQueue();
    amqpAdmin.declareBinding(BindingBuilder.bind(commands).to(new TopicExchange(GUEST_STAYS_EXCHANGE)).with(CheckOutGuest.class.getName()));
    amqpAdmin.declareBinding(BindingBuilder.bind(events).to(new TopicExchange(GUEST_STAYS_EXCHANGE)).with(GuestCheckedOut.class.getName()));

    messageBus.publish(List.of(command, event));

    assertThat(rabbitTemplate.receiveAndConvert(commands.getName(), 5000)).isEqualTo(command);
    assertThat(rabbitTemplate.receiveAndConvert(events.getName(), 5000)).isEqualTo(event);
  }
}
