package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CoreConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageMapping;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageSerializationConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.GuestCheckedIn;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
    var message = deserialize("room-inspected", "{\"roomId\":\"101\",\"inspectorId\":\"anna\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "anna"));
  }

  @Test
  public void messageOfAnUnregisteredType_IsReadByItsClassName() {
    var guestStayId = UUID.randomUUID();
    var openedAt = OffsetDateTime.parse("2026-11-07T10:15:30+01:00");

    var message = deserialize(
      GuestCheckedIn.class.getName(),
      "{\"guestStayAccountId\":\"" + guestStayId + "\",\"openedAt\":\"2026-11-07T10:15:30+01:00\"}"
    );

    assertThat(message).isEqualTo(new GuestCheckedIn(guestStayId, openedAt));
  }

  @Test
  public void oldVersionOfAMessage_IsTransformedFromItsOldClass() {
    var message = deserialize("room-inspected-v1", "{\"room\":\"101\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "unknown"));
  }

  @Test
  public void oldVersionOfAMessage_IsTransformedFromItsJson() {
    var message = deserialize("room-inspected-v0", "{\"r\":\"101\"}");

    assertThat(message).isEqualTo(new RoomInspected("101", "unknown"));
  }

  @Test
  public void oldVersionWithBrokenJson_IsUnreadable() {
    assertThatThrownBy(() -> deserialize("room-inspected-v1", "{\"room\":"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("room-inspected-v1");
  }

  @Test
  public void messageOfUnknownType_IsUnreadable() {
    assertThatThrownBy(() -> deserialize("UnknownMessage", "{}"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("UnknownMessage");
  }

  @Test
  public void messageWithBrokenJson_IsUnreadable() {
    assertThatThrownBy(() -> deserialize("room-inspected", "{\"roomId\":"))
      .isInstanceOf(UnreadableMessageException.class)
      .hasMessageContaining("room-inspected");
  }

  private Object deserialize(String messageType, String json) {
    var headers = new RecordHeaders();
    headers.add(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, messageType.getBytes());

    return converter.read(new ConsumerRecord<>("topic", 0, 0, 0L, TimestampType.CREATE_TIME, 0, 0, null, json.getBytes(), headers, Optional.empty()));
  }

  private static VersionedMessageConverter converter(MessageMapping mapping) {
    var jsonMapper = new CoreConfiguration().jsonMapper();
    var configuration = new MessageSerializationConfiguration();
    var registry = configuration.messageMappingRegistry(List.of(mapping), jsonMapper);

    return new VersionedMessageConverter(
      configuration.kafkaJsonDeserializer(configuration.kafkaTypeMapper(registry, new String[]{"*"}), jsonMapper),
      jsonMapper,
      registry.getMessageTransformations()
    );
  }
}
