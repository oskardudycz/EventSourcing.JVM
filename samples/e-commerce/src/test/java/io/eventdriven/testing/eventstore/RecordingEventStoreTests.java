package io.eventdriven.testing.eventstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class RecordingEventStoreTests {
    private final InMemoryEventStore store = new InMemoryEventStore(
            TestTracing.untraced(), TestMessages.serializerTrustingPackageOf(RecordingEventStoreTests.class));
    private final RecordingEventStore eventStore =
            new RecordingEventStore(new ConfirmingRightAfterEventStore(store, () -> this.eventStore));
    private final String streamId = "Cart-" + UUID.randomUUID();

    @Test
    public void anEventAnotherWriterAppendsRightAfterIsRecordedOnceInTheOrderOfTheStore() {
        eventStore.append(streamId, new Opened());

        assertThat(eventStore.appended().stream().map(MessageEnvelope::message).toList())
                .containsExactly(new Opened(), new Confirmed());
    }

    record Opened() {}

    record Confirmed() {}

    /**
     * Another writer that appends {@link Confirmed} through {@code writer} as soon as {@link Opened} is in the stream,
     * before the append of {@link Opened} returns.
     */
    record ConfirmingRightAfterEventStore(EventStore store, Supplier<EventStore> writer) implements EventStore {
        @Override
        public ReadResult read(String streamId) {
            return store.read(streamId);
        }

        @Override
        public AppendResult append(String streamId, Object... events) {
            return confirmingAfter(streamId, store.append(streamId, events), events);
        }

        @Override
        public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
            return confirmingAfter(streamId, store.append(streamId, expectedVersion, events), events);
        }

        @Override
        public Subscription subscribe(String name, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
            return store.subscribe(name, types, handler);
        }

        @Override
        public void whenCaughtUp(Duration timeout) {
            store.whenCaughtUp(timeout);
        }

        private AppendResult confirmingAfter(String streamId, AppendResult result, Object[] events) {
            if (events[0] instanceof Opened)
                writer.get().append(streamId, ExpectedStreamVersion.at(0), new Confirmed());
            return result;
        }
    }
}
