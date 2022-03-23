package io.eventdriven.ecommerce.core.subscriptions;

import com.eventstore.dbclient.*;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class EventStoreDBSubscriptionCheckpointRepository implements SubscriptionCheckpointRepository {
  private final EventStoreDBClient eventStore;
  private final Logger logger = LoggerFactory.getLogger(EventStoreDBSubscriptionCheckpointRepository.class);

  public EventStoreDBSubscriptionCheckpointRepository(
    EventStoreDBClient eventStore
  ) {
    this.eventStore = eventStore;
  }

  public Optional<Long> load(String subscriptionId) throws InterruptedException, ExecutionException {
    var streamName = getCheckpointStreamName(subscriptionId);

    var readOptions = ReadStreamOptions.get()
      .backwards()
      .fromEnd();

    try {
      return eventStore.readStream(streamName, readOptions)
        .get()
        .getEvents()
        .stream()
        .map(e -> EventSerializer.<CheckpointStored>deserialize(e).map(ch -> ch.position()))
        .findFirst()
        .orElse(Optional.empty());

    } catch (ExecutionException e) {
      Throwable innerException = e.getCause();

      if (!(innerException instanceof StreamNotFoundException)) {
        logger.error("Failed to load checkpoint", e);
        throw e;
      }
      return Optional.empty();
    }
  }

  public void store(String subscriptionId, long position) throws ExecutionException, InterruptedException {
    var event = EventSerializer.serialize(
      new CheckpointStored(subscriptionId, position, LocalDateTime.now())
    );

    var streamName = getCheckpointStreamName(subscriptionId);

    try {
      // store new checkpoint expecting stream to exist
      eventStore.appendToStream(
        streamName,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.STREAM_EXISTS),
        event
      ).get();
    } catch (ExecutionException ex) {
      if (!(ex.getCause() instanceof WrongExpectedVersionException))
        throw ex;

      // WrongExpectedVersionException means that stream did not exist
      // Set the checkpoint stream to have at most 1 event
      // using stream metadata $maxCount property

      var keepOnlyLastEvent = new StreamMetadata();
      keepOnlyLastEvent.setMaxCount(1);

      eventStore.setStreamMetadata(
        streamName,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        keepOnlyLastEvent
      ).get();

      // append event again expecting stream to not exist
      eventStore.appendToStream(
        streamName,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        event
      ).get();
    }
  }

  private static String getCheckpointStreamName(String subscriptionId) {
    return "checkpoint_%s".formatted(subscriptionId);
  }

  public EventStoreDBClient eventStore() {
    return eventStore;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (EventStoreDBSubscriptionCheckpointRepository) obj;
    return Objects.equals(this.eventStore, that.eventStore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventStore);
  }

  @Override
  public String toString() {
    return "EventStoreDBSubscriptionCheckpointRepository[" +
      "eventStore=" + eventStore + ']';
  }

}
