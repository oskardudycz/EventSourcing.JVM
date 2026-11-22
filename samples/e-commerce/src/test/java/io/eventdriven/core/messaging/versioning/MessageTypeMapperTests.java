package io.eventdriven.core.messaging.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

public class MessageTypeMapperTests {
    record RoomInspected(String roomId) {}

    record RoomCleaned(String roomId) {}

    @Test
    public void aNestedRecordIsNamedByItsJavaClassName() {
        var typeMapper = new MessageTypeMapper(
                List.of(new MessageMapping().trust(MessageTypeMapperTests.class.getPackageName())));

        assertThat(typeMapper.nameOf(RoomInspected.class))
                .isEqualTo("io.eventdriven.core.messaging.versioning." + "MessageTypeMapperTests$RoomInspected");
        assertThat(typeMapper.classOf(typeMapper.nameOf(RoomInspected.class))).contains(RoomInspected.class);
    }

    @Test
    public void anExplicitNameIsWrittenAndReadBack() {
        var typeMapper =
                new MessageTypeMapper(List.of(new MessageMapping().register("rooms.Inspected", RoomInspected.class)));

        assertThat(typeMapper.nameOf(RoomInspected.class)).isEqualTo("rooms.Inspected");
        assertThat(typeMapper.classOf("rooms.Inspected")).contains(RoomInspected.class);
    }

    @Test
    public void mappingsOfSeveralModulesAreMerged() {
        var typeMapper = new MessageTypeMapper(List.of(
                new MessageMapping().register("rooms.Inspected", RoomInspected.class),
                new MessageMapping().register("rooms.Cleaned", RoomCleaned.class)));

        assertThat(typeMapper.classOf("rooms.Inspected")).contains(RoomInspected.class);
        assertThat(typeMapper.classOf("rooms.Cleaned")).contains(RoomCleaned.class);
    }

    @Test
    public void twoClassesUnderOneExplicitNameFailNamingBoth() {
        var mappings = List.of(
                new MessageMapping().register("rooms.Inspected", RoomInspected.class),
                new MessageMapping().register("rooms.Inspected", RoomCleaned.class));

        assertThatThrownBy(() -> new MessageTypeMapper(mappings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rooms.Inspected")
                .hasMessageContaining(RoomInspected.class.getName())
                .hasMessageContaining(RoomCleaned.class.getName());
    }

    @Test
    public void aClassOutsideTheTrustedPackagesHasNoClass() {
        var typeMapper = new MessageTypeMapper(List.of(new MessageMapping().trust("some.other.package")));

        assertThat(typeMapper.classOf(RoomInspected.class.getName())).isEmpty();
    }
}
