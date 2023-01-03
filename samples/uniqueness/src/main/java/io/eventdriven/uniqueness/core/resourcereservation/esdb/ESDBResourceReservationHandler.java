package io.eventdriven.uniqueness.core.resourcereservation.esdb;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.ExpectedRevision;
import io.eventdriven.uniqueness.core.esdb.EventStore;
import io.eventdriven.uniqueness.core.processing.HandlerWithAck;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationHandler;
import io.eventdriven.uniqueness.core.retries.RetryPolicy;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;

import static io.eventdriven.uniqueness.core.resourcereservation.esdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.processing.HandlerWithAckProcessor.*;

public class ESDBResourceReservationHandler implements ResourceReservationHandler {
  private static final Logger logger = LoggerFactory.getLogger(ESDBResourceReservationHandler.class);
  private final Duration reservationLockDuration;
  private final EventStore eventStore;
  private final RetryPolicy retryPolicy;

  public ESDBResourceReservationHandler(
    Duration reservationLockDuration,
    RetryPolicy retryPolicy,
    EventStore eventStore
  ) {
    this.reservationLockDuration = reservationLockDuration;
    this.eventStore = eventStore;
    this.retryPolicy = retryPolicy;
  }

  @Override
  public Boolean reserve(String resourceKey, HandlerWithAck<Boolean> onReserved) {
    try {
      final var reservationStreamId = streamName(resourceKey);

      var initiationResult = initiateReservation(
        resourceKey,
        reservationStreamId,
        reservationLockDuration
      );

      if (!(initiationResult instanceof EventStore.AppendResult.Success success)) {
        logger.error("Failed to reserve '%s'".formatted(reservationStreamId));
        return false;
      }

      var succeeded = run(onReserved).orElse(false);

      if (!succeeded) {
        markReservationAsReleased(resourceKey, reservationStreamId, success.nextExpectedRevision());
        return false;
      }

      var confirmationReservation = confirmReservation(
        resourceKey,
        reservationStreamId,
        success.nextExpectedRevision()
      );

      return confirmationReservation instanceof EventStore.AppendResult.Success;
    } catch (Throwable e) {
      logger.error("Error while reserving resource");
      return false;
    }
  }

  @Override
  public void release(String resourceKey) {
    var result = eventStore.deleteStream(streamName(resourceKey));

    if (result instanceof EventStore.DeleteResult.UnexpectedFailure) {
      throw new RuntimeException("Error while deleting stream: %s".formatted(result));
    }
  }

  private EventStore.AppendResult initiateReservation(String resourceKey, String reservationStreamId, Duration tentativeLockDuration) {
    final var reservationInitiated = new ResourceReservationInitiated(
      resourceKey,
      OffsetDateTime.now(),
      tentativeLockDuration
    );

    return retryPolicy.run(ack -> {
      var result = eventStore.append(reservationStreamId, reservationInitiated);

      if (!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private EventStore.AppendResult confirmReservation(String resourceKey, String reservationStreamId, ExpectedRevision expectedRevision) {
    final var reservationConfirmed = new ResourceReservationConfirmed(
      resourceKey,
      OffsetDateTime.now()
    );

    return retryPolicy.run(ack -> {
      var result = eventStore.append(reservationStreamId, expectedRevision, reservationConfirmed);

      if (!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private void markReservationAsReleased(String resourceKey, String reservationStreamId, ExpectedRevision expectedRevision) {
    // We're marking reservation as to be released instead of deleting stream.
    // That's needed as if we'd delete stream here, then we wouldn't get event notification through subscriptions.
    // Because of that we wouldn't be able to clear the lookup for timed out reservations.
    final var reservationReleased = new ResourceReservationReleaseInitiated(
      resourceKey,
      OffsetDateTime.now()
    );

    retryPolicy.run(ack -> {
      var result = eventStore.append(
        reservationStreamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        EventSerializer.serialize(reservationReleased)
      );

      if (!(result instanceof EventStore.AppendResult.UnexpectedFailure))
        ack.accept(result);
    });
  }

  private static String streamName(String resourceKey) {
    return "reservation-%s".formatted(resourceKey);
  }
}
