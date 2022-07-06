package io.eventdriven.uniqueness.core.esdb.reservations;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.StreamRevision;
import io.eventdriven.uniqueness.core.esdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservation;
import io.eventdriven.uniqueness.core.retries.RetryPolicy;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ESDBResourceReservation<R> implements ResourceReservation<R> {
  record ResourceReservationInitiated(
    String resourceKey,
    LocalDateTime initiatedAt,
    Duration tentativeLockFor) {
  }

  record ResourceReservationConfirmed(
    String resourceKey,
    LocalDateTime reservedAt) {
  }

  private static final Logger logger = LoggerFactory.getLogger(ESDBResourceReservation.class);
  private final Function<R, String> getResourceKey;
  private final Function<R, Duration> getReservationDuration;
  private final EventStore eventStore;
  private final RetryPolicy retryPolicy;

  public ESDBResourceReservation(
    Function<R, String> getResourceKey,
    Function<R, Duration> getTentativeLockDuration,
    RetryPolicy retryPolicy,
    EventStore eventStore
  ) {
    this.getResourceKey = getResourceKey;
    this.getReservationDuration = getTentativeLockDuration;
    this.eventStore = eventStore;
    this.retryPolicy = retryPolicy;
  }

  @Override
  public boolean reserve(R resource, Supplier<Boolean> onReserved) {
    try {
      final var resourceKey = getResourceKey.apply(resource);
      final var reservationStreamId = "reservation-%s".formatted(resourceKey);

      var initiationResult = initiateReservation(
        resourceKey,
        reservationStreamId,
        getReservationDuration.apply(resource)
      );

      if (!(initiationResult instanceof EventStore.AppendResult.Success success)) {
        logger.error("Failed to reserve '%s'".formatted(reservationStreamId));
        return false;
      }

      var businessLogicSucceeded = onReserved.get();

      if (!businessLogicSucceeded) {
        releaseReservation(reservationStreamId, success.nextExpectedRevision());
        return false;
      }

      confirmReservation(resourceKey, reservationStreamId, success.nextExpectedRevision());

      return true;
    } catch (Throwable e) {
      logger.error("Error while reserving resource");
      return false;
    }
  }

  private EventStore.AppendResult initiateReservation(String resourceKey, String reservationStreamId, Duration tentativeLockDuration) {
    final var reservationInitiated = new ResourceReservationInitiated(
      resourceKey,
      LocalDateTime.now(),
      tentativeLockDuration
    );

    return retryPolicy.run(ack -> {
      var result = eventStore.append(reservationStreamId, reservationInitiated);

      if(!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private void confirmReservation(String resourceKey, String reservationStreamId, StreamRevision expectedRevision) {
    final var reservationConfirmed = new ResourceReservationConfirmed(
      resourceKey,
      LocalDateTime.now()
    );

    retryPolicy.run(ack -> {
      var result = eventStore.append(
        reservationStreamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(reservationConfirmed)
      );

      if(!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private void releaseReservation(String reservationStreamId, StreamRevision expectedRevision) {
    retryPolicy.run(ack -> {
      var result = eventStore.deleteStream(
        reservationStreamId,
        expectedRevision
      );

      if(!(result instanceof EventStore.DeleteResult.UnexpectedFailure))
        ack.accept(result);
    });
  }
}
