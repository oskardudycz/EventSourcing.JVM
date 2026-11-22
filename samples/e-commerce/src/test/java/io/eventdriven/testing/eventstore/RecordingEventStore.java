package io.eventdriven.testing.eventstore;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.messaging.MessageEnvelope;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Keeps every event that the application appended, read back with the metadata that the store gave it, so a test can
 * check what was appended and which message caused it.
 */
public class RecordingEventStore implements EventStore, AutoCloseable {
    private final EventStore store;
    private final List<MessageEnvelope<Object>> appended = new CopyOnWriteArrayList<>();

    public RecordingEventStore(EventStore store) {
        this.store = store;
    }

    public List<MessageEnvelope<Object>> appended() {
        return appended;
    }

    @Override
    public ReadResult read(String streamId) {
        return store.read(streamId);
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return recorded(streamId, events.length, store.append(streamId, events));
    }

    @Override
    public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        return recorded(streamId, events.length, store.append(streamId, expectedVersion, events));
    }

    @Override
    public Subscription subscribe(String name, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        return store.subscribe(name, types, handler);
    }

    @Override
    public void whenCaughtUp(Duration timeout) {
        store.whenCaughtUp(timeout);
    }

    @Override
    public void close() throws Exception {
        if (store instanceof AutoCloseable closeable) closeable.close();
    }

    private AppendResult recorded(String streamId, int count, AppendResult result) {
        if (result instanceof AppendResult.Success(var nextStreamVersion, var lastEvent)
                && store.read(streamId) instanceof ReadResult.Success(var events))
            events.stream()
                    .filter(event -> event.metadata().stream().streamPosition() > nextStreamVersion - count
                            && event.metadata().stream().streamPosition() <= nextStreamVersion)
                    .forEach(this::recordInLogOrder);

        return result;
    }

    private synchronized void recordInLogOrder(MessageEnvelope<Object> event) {
        var logPosition = event.metadata().stream().logPosition();
        var index = appended.size();
        while (index > 0 && appended.get(index - 1).metadata().stream().logPosition() > logPosition) index--;
        appended.add(index, event);
    }
}
