package io.eventdriven.uniqueness.core.resourcereservation.kurrentdb;

import io.kurrent.dbclient.AppendToStreamOptions;
import io.kurrent.dbclient.StreamState;
import io.eventdriven.uniqueness.core.kurrentdb.EventStore;
import io.eventdriven.uniqueness.core.processing.HandlerWithAck;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationHandler;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;

import static io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.processing.HandlerWithAckProcessor.*;

public class KurrentDBResourceReservationHandler implements ResourceReservationHandler {
  private static final Logger logger = LoggerFactory.getLogger(KurrentDBResourceReservationHandler.class);
  private final Duration reservationLockDuration;
  private final EventStore eventStore;
  private final RetryTemplate retryTemplate;

  public KurrentDBResourceReservationHandler(
    Duration reservationLockDuration,
    RetryTemplate retryTemplate,
    EventStore eventStore
  ) {
    this.reservationLockDuration = reservationLockDuration;
    this.eventStore = eventStore;
    this.retryTemplate = retryTemplate;
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

    return retryTemplate.invoke(() -> {
      var result = eventStore.append(reservationStreamId, reservationInitiated);

      if (result instanceof EventStore.AppendResult.UnexpectedFailure)
        throw new IllegalStateException("Unexpected failure while appending: %s".formatted(result));

      return result;
    });
  }

  private EventStore.AppendResult confirmReservation(String resourceKey, String reservationStreamId, StreamState expectedRevision) {
    final var reservationConfirmed = new ResourceReservationConfirmed(
      resourceKey,
      OffsetDateTime.now()
    );

    return retryTemplate.invoke(() -> {
      var result = eventStore.append(reservationStreamId, expectedRevision, reservationConfirmed);

      if (result instanceof EventStore.AppendResult.UnexpectedFailure)
        throw new IllegalStateException("Unexpected failure while appending: %s".formatted(result));

      return result;
    });
  }

  private void markReservationAsReleased(String resourceKey, String reservationStreamId, StreamState expectedRevision) {
    // We're marking reservation as to be released instead of deleting stream.
    // That's needed as if we'd delete stream here, then we wouldn't get event notification through subscriptions.
    // Because of that we wouldn't be able to clear the lookup for timed out reservations.
    final var reservationReleased = new ResourceReservationReleaseInitiated(
      resourceKey,
      OffsetDateTime.now()
    );

    retryTemplate.invoke(() -> {
      var result = eventStore.append(
        reservationStreamId,
        AppendToStreamOptions.get().streamState(expectedRevision),
        EventSerializer.serialize(reservationReleased)
      );

      if (result instanceof EventStore.AppendResult.UnexpectedFailure)
        throw new IllegalStateException("Unexpected failure while appending: %s".formatted(result));
    });
  }

  private static String streamName(String resourceKey) {
    return "reservation-%s".formatted(resourceKey);
  }
}
