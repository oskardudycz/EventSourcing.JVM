package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedOut;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes.inPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = KafkaSagaTestApplication.class)
@TestPropertySource(properties = {
  "app.kafka.topic=message-routing",
  "app.kafka.group-id=message-routing"
})
@Import(KafkaMessageRoutingTest.Routes.class)
@AutoConfigureTracing
public class KafkaMessageRoutingTest {
  private static final String ROOMS_TOPIC = "message-routing-rooms";
  private static final String GUEST_STAYS_TOPIC = "message-routing-guest-stays";

  public record RoomInspected(String roomId, String notes) {
  }

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
        .routeKey(GuestMoved.class, GuestMoved::guestId)
        .route(HousekeepingRequested.class, message -> new RoutingTarget(ROOMS_TOPIC, message.roomId()))
        .routeTarget(RoomRepaired.class, message -> ROOMS_TOPIC)
        .routeTarget(inPackage(GuestCheckedOut.class.getPackageName()), GUEST_STAYS_TOPIC);
    }

    @Bean
    NewTopic guestStaysTopic() {
      return TopicBuilder.name(GUEST_STAYS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic roomsTopic() {
      return TopicBuilder.name(ROOMS_TOPIC).partitions(3).replicas(1).build();
    }
  }

  @Autowired
  private KafkaMessageBus messageBus;

  @Autowired
  private ConsumerFactory<?, ?> consumerFactory;

  @Value("${app.kafka.topic}")
  private String topic;

  @Test
  public void record_IsKeyedByItsFirstComponent() {
    var message = new RoomInspected(UUID.randomUUID().toString(), "clean");

    messageBus.publish(List.of(message));

    assertThat(publishedRecord(topic, message.roomId()).key()).isEqualTo(message.roomId());
  }

  @Test
  public void record_IsKeyedByTheRegisteredKey() {
    var message = new GuestMoved(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    messageBus.publish(List.of(message));

    assertThat(publishedRecord(topic, message.hotelId()).key()).isEqualTo(message.guestId());
  }

  @Test
  public void record_IsSentToTheRegisteredTopicWithTheRegisteredKey() {
    var message = new HousekeepingRequested(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    messageBus.publish(List.of(message));

    assertThat(publishedRecord(ROOMS_TOPIC, message.hotelId()).key()).isEqualTo(message.roomId());
  }

  @Test
  public void record_IsSentToTheRegisteredTopicKeyedByItsFirstComponent() {
    var message = new RoomRepaired(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    messageBus.publish(List.of(message));

    assertThat(publishedRecord(ROOMS_TOPIC, message.hotelId()).key()).isEqualTo(message.roomId());
  }

  @Test
  public void messagesFromAPackage_AreSentToItsRegisteredTopicKeyedByTheirFirstComponent() {
    var guestStayId = UUID.randomUUID();
    var now = OffsetDateTime.now();
    var command = new CheckOutGuest(guestStayId, now, UUID.randomUUID());
    var event = new GuestCheckedOut(guestStayId, now, UUID.randomUUID());

    messageBus.publish(List.of(command, event));

    assertThat(publishedRecord(GUEST_STAYS_TOPIC, command.groupCheckOutId().toString()).key())
      .isEqualTo(guestStayId.toString());
    assertThat(publishedRecord(GUEST_STAYS_TOPIC, event.groupCheckoutId().toString()).key())
      .isEqualTo(guestStayId.toString());
  }

  private ConsumerRecord<String, String> publishedRecord(String topic, String containing) {
    return await().atMost(Duration.ofSeconds(10))
      .until(() -> findRecord(topic, containing), Optional::isPresent)
      .get();
  }

  private Optional<ConsumerRecord<String, String>> findRecord(String topic, String containing) {
    var overrides = new Properties();
    overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    var rawConsumerFactory = new DefaultKafkaConsumerFactory<>(
      consumerFactory.getConfigurationProperties(),
      new StringDeserializer(),
      new StringDeserializer()
    );

    try (var consumer = rawConsumerFactory.createConsumer("route-reader-" + UUID.randomUUID(), null, null, overrides)) {
      consumer.subscribe(List.of(topic));

      for (var record : consumer.poll(Duration.ofSeconds(2))) {
        if (record.value().contains(containing))
          return Optional.of(record);
      }
      return Optional.empty();
    }
  }
}
