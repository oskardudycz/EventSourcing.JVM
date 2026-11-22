package io.eventdriven.core.messaging.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.testing.messaging.TestMessages;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class MessageSerializerTests {
    record RoomInspected(String roomId) {}

    record RoomInspectedV1(String room) {}

    private final MessageSerializer serializer = TestMessages.serializer(new MessageMapping()
            .trust(MessageSerializerTests.class.getPackageName())
            .register("rooms.RoomInspected", RoomInspected.class)
            .register(
                    "rooms.RoomInspected.v1",
                    RoomInspectedV1.class,
                    RoomInspected.class,
                    old -> new RoomInspected(old.room()))
            .register(
                    "rooms.RoomInspected.v0",
                    RoomInspected.class,
                    json -> new RoomInspected(json.get("r").asString())));

    @Test
    public void aMessageWithAnExplicitNameIsWrittenUnderIt() {
        assertThat(serializer.typeNameOf(RoomInspected.class)).isEqualTo("rooms.RoomInspected");
    }

    @Test
    public void aMessageWithoutAnExplicitNameIsWrittenUnderItsClassName() {
        record Unmapped(String id) {}

        assertThat(serializer.typeNameOf(Unmapped.class)).isEqualTo(Unmapped.class.getName());
    }

    @Test
    public void aTypeNameIsKnownWhenAModuleMapsItOrTrustsItsPackage() {
        assertThat(serializer.isKnown("rooms.RoomInspected")).isTrue();
        assertThat(serializer.isKnown("rooms.RoomInspected.v0")).isTrue();
        assertThat(serializer.isKnown(RoomInspectedV1.class.getName())).isTrue();
        assertThat(serializer.isKnown("rooms.Unknown")).isFalse();
        assertThat(serializer.isKnown("java.lang.ProcessBuilder")).isFalse();
    }

    @Test
    public void aMessageIsKnownUnderItsCurrentAndOldNames() {
        assertThat(serializer.typeNamesOf(RoomInspected.class))
                .containsExactlyInAnyOrder("rooms.RoomInspected", "rooms.RoomInspected.v1", "rooms.RoomInspected.v0");
    }

    @Test
    public void consumerMapsTheExplicitTypeNameToTheCurrentClass() {
        var body = "{\"roomId\":\"room-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read("rooms.RoomInspected", body)).isEqualTo(new RoomInspected("room-1"));
    }

    @Test
    public void aClassNameInsideATrustedPackageIsRead() {
        var body = "{\"roomId\":\"room-1\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.read(RoomInspected.class.getName(), body)).isEqualTo(new RoomInspected("room-1"));
    }

    @Test
    public void aClassNameOutsideTheTrustedPackagesIsNotRead() {
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
