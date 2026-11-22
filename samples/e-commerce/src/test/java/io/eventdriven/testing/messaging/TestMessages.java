package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.versioning.MessageMapping;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.messaging.versioning.MessageTypeMapper;
import java.util.Arrays;
import java.util.List;

public final class TestMessages {
    private TestMessages() {}

    public static MessageSerializer serializer(MessageMapping... mappings) {
        return serializer(List.of(mappings));
    }

    public static MessageSerializer serializer(List<MessageMapping> mappings) {
        return new MessageSerializer(new MessageTypeMapper(mappings), MessageSerializer.newJsonMapper());
    }

    public static MessageSerializer serializerTrustingPackageOf(Class<?>... classes) {
        return serializer(trustingPackageOf(classes));
    }

    public static MessageMapping trustingPackageOf(Class<?>... classes) {
        return new MessageMapping()
                .trust(Arrays.stream(classes)
                        .map(Class::getPackageName)
                        .distinct()
                        .toArray(String[]::new));
    }
}
