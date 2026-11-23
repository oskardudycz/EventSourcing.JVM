package io.eventdriven.testing.contracts;

import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.strictland.SpecificationOptions;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;

public final class Contracts {
    public static final SpecificationOptions options =
            SpecificationOptions.serializer(new JsonMapperSerializer(MessageSerializer.newJsonMapper()));

    public static final OffsetDateTime at = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private Contracts() {}

    public static UUID uuid(int number) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(number));
    }

    public static Stream<Named<Object>> examples(Object... messages) {
        return Arrays.stream(messages)
                .map(message -> Named.of(message.getClass().getSimpleName(), message));
    }
}
