package io.eventdriven.ecommerce.core.subscriptions;

import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import io.kurrent.dbclient.*;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KurrentDBSubscriptionCheckpointRepository implements SubscriptionCheckpointRepository {
    private final KurrentDBClient eventStore;
    private final Logger logger = LoggerFactory.getLogger(KurrentDBSubscriptionCheckpointRepository.class);

    public KurrentDBSubscriptionCheckpointRepository(KurrentDBClient eventStore) {
        this.eventStore = eventStore;
    }

    public Optional<Long> load(String subscriptionId) {
        var streamName = getCheckpointStreamName(subscriptionId);

        var readOptions = ReadStreamOptions.get().backwards().fromEnd();

        try {
            return eventStore.readStream(streamName, readOptions).get().getEvents().stream()
                    .map(e -> EventSerializer.<CheckpointStored>deserialize(e).map(ch -> ch.position()))
                    .findFirst()
                    .orElse(Optional.empty());

        } catch (Throwable e) {
            Throwable innerException = e.getCause();

            if (!(innerException instanceof StreamNotFoundException)) {
                logger.error("Failed to load checkpoint", e);
                throw new RuntimeException(e);
            }
            return Optional.empty();
        }
    }

    public void store(String subscriptionId, long position) {
        var event = EventSerializer.serialize(new CheckpointStored(subscriptionId, position, OffsetDateTime.now()));

        var streamName = getCheckpointStreamName(subscriptionId);

        try {
            // store new checkpoint expecting stream to exist
            eventStore
                    .appendToStream(
                            streamName, AppendToStreamOptions.get().streamState(StreamState.streamExists()), event)
                    .get();
        } catch (Throwable e) {
            if (!(e.getCause() instanceof WrongExpectedVersionException)) throw new RuntimeException(e);

            // WrongExpectedVersionException means that stream did not exist
            // Set the checkpoint stream to have at most 1 event
            // using stream metadata $maxCount property

            var keepOnlyLastEvent = new StreamMetadata();
            keepOnlyLastEvent.setMaxCount(1L);

            try {
                eventStore
                        .setStreamMetadata(
                                streamName,
                                AppendToStreamOptions.get().streamState(StreamState.noStream()),
                                keepOnlyLastEvent)
                        .get();

                // append event again expecting stream to not exist
                eventStore
                        .appendToStream(
                                streamName, AppendToStreamOptions.get().streamState(StreamState.noStream()), event)
                        .get();
            } catch (Exception exception) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String getCheckpointStreamName(String subscriptionId) {
        return "checkpoint-%s".formatted(subscriptionId);
    }
}
