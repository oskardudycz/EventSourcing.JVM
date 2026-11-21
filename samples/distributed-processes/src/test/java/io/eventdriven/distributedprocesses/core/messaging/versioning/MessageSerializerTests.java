package io.eventdriven.distributedprocesses.core.messaging.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.distributedprocesses.core.messaging.UnreadableMessageException;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class MessageSerializerTests {
    record RoomInspected(String roomId) {}

    record RoomInspectedV1(String room) {}

    private final MessageSerializer serializer = new MessageSerializer(
            new MessageMapping()
                    .register("rooms.RoomInspected", RoomInspected.class)
                    .register("rooms.RoomInspected.v1", RoomInspectedV1.class, old -> new RoomInspected(old.room()))
                    .register(
                            "rooms.RoomInspected.v0",
                            json -> new RoomInspected(json.get("r").asString())),
            EventSerializer.mapper,
            "io.eventdriven.distributedprocesses.core.messaging");

    @Test
    public void aMessageIsWrittenUnderItsStableTypeName() {
        assertThat(serializer.typeNameOf(RoomInspected.class)).isEqualTo("rooms.RoomInspected");
    }

    @Test
    public void aMessageWithoutAStableTypeNameCannotBeNamed() {
        record Unmapped(String id) {}

        assertThatThrownBy(() -> serializer.typeNameOf(Unmapped.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Unmapped.class.getName());
    }

    @Test
    public void currentAndOldTypeNamesAreRegisteredAndOthersAreNot() {
        assertThat(serializer.isRegistered("rooms.RoomInspected")).isTrue();
        assertThat(serializer.isRegistered("rooms.RoomInspected.v0")).isTrue();
        assertThat(serializer.isRegistered("rooms.Unknown")).isFalse();
    }

    @Test
    public void consumerMapsTheStableTypeNameToTheCurrentClass() {
        var body = "{\"roomId\":\"room-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read("rooms.RoomInspected", body)).isEqualTo(new RoomInspected("room-1"));
    }

    @Test
    public void aClassNameInsideTheTrustedPackageIsRead() {
        var body = "{\"roomId\":\"room-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read(RoomInspected.class.getName(), body)).isEqualTo(new RoomInspected("room-1"));
    }

    @Test
    public void aClassNameOutsideTheTrustedPackageIsNotRead() {
        assertThatThrownBy(() -> serializer.read("java.lang.ProcessBuilder", "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining("java.lang.ProcessBuilder");
    }

    @Test
    public void aBodyThatIsNotJsonIsNotRead() {
        assertThatThrownBy(() -> serializer.read("rooms.RoomInspected", "not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining("rooms.RoomInspected");
    }

    @Test
    public void oldVersionOfAMessageIsTransformedFromItsOldClass() {
        var body = "{\"room\":\"101\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read("rooms.RoomInspected.v1", body)).isEqualTo(new RoomInspected("101"));
    }

    @Test
    public void oldVersionOfAMessageIsTransformedFromItsJson() {
        var body = "{\"r\":\"101\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read("rooms.RoomInspected.v0", body)).isEqualTo(new RoomInspected("101"));
    }

    @Test
    public void oldVersionWithABodyThatIsNotJsonIsNotRead() {
        assertThatThrownBy(
                        () -> serializer.read("rooms.RoomInspected.v1", "{\"room\":".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining("rooms.RoomInspected.v1");
    }
}
