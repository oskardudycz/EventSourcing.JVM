package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CoreConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageMapping;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageSerializationConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.DefaultClassMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VersionedMessageConverterTest {
  public record RoomInspected(String roomId, String inspectorId) {
  }

  public record RoomInspectedV1(String room) {
  }

  private final VersionedMessageConverter converter = converter(new MessageMapping()
    .register("room-inspected", RoomInspected.class)
    .register("room-inspected-v1", RoomInspectedV1.class, old -> new RoomInspected(old.room(), "unknown"))
    .register("room-inspected-v0", json -> new RoomInspected(json.get("r").asString(), "unknown"))
  );

  @Test
  public void messageOfARegisteredType_IsReadAsThatType() {
    var message = read("room-inspected", "{\"roomId\":\"101\",\"inspectorId\":\"anna\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "anna"));
  }

  @Test
  public void messageOfAnUnregisteredType_IsReadByItsClassName() {
    var guestStayId = UUID.randomUUID();
    var openedAt = OffsetDateTime.parse("2026-11-07T10:15:30+01:00");

    var message = read(
      GuestCheckedIn.class.getName(),
      "{\"guestStayAccountId\":\"" + guestStayId + "\",\"openedAt\":\"2026-11-07T10:15:30+01:00\"}"
    );

    assertThat(message).isEqualTo(new GuestCheckedIn(guestStayId, openedAt));
  }

  @Test
  public void oldVersionOfAMessage_IsTransformedFromItsOldClass() {
    var message = read("room-inspected-v1", "{\"room\":\"101\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "unknown"));
  }

  @Test
  public void oldVersionOfAMessage_IsTransformedFromItsJson() {
    var message = read("room-inspected-v0", "{\"r\":\"101\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "unknown"));
  }

  @Test
  public void oldVersionWithBrokenJson_IsUnreadable() {
    assertThatThrownBy(() -> read("room-inspected-v1", "{\"room\":"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("room-inspected-v1");
  }

  @Test
  public void messageOfUnknownType_IsUnreadable() {
    assertThatThrownBy(() -> read("UnknownMessage", "{}"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("UnknownMessage");
  }

  @Test
  public void messageWithBrokenJson_IsUnreadable() {
    assertThatThrownBy(() -> read("room-inspected", "{\"roomId\":"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("room-inspected");
  }

  private Object read(String messageType, String json) {
    return converter.read(MessageBuilder.withBody(json.getBytes())
      .setContentType(MessageProperties.CONTENT_TYPE_JSON)
      .setHeader(DefaultClassMapper.DEFAULT_CLASSID_FIELD_NAME, messageType)
      .build());
  }

  private static VersionedMessageConverter converter(MessageMapping mapping) {
    var jsonMapper = new CoreConfiguration().jsonMapper();
    var registry = new MessageSerializationConfiguration().messageMappingRegistry(List.of(mapping), jsonMapper);

    return new VersionedMessageConverter(
      jsonMapper,
      registry.getMessageTransformations(),
      new RabbitConfig().rabbitMessageConverter(registry, jsonMapper, new String[]{"*"})
    );
  }
}
