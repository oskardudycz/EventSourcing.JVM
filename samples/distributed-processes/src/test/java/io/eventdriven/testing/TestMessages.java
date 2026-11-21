package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.Application;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;

public final class TestMessages {
    private TestMessages() {}

    public static MessageSerializer serializer(MessageMapping mapping) {
        return new MessageSerializer(mapping, EventSerializer.mapper, Application.class.getPackageName());
    }
}
