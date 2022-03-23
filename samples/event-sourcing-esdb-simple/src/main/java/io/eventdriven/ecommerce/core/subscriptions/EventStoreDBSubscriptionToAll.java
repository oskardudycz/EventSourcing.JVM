package io.eventdriven.ecommerce.core.subscriptions;

import com.eventstore.dbclient.*;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.events.EventEnvelope;
import io.eventdriven.ecommerce.core.events.EventTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ExecutionException;

public class EventStoreDBSubscriptionToAll {
  private final EventStoreDBClient eventStoreClient;
  private final SubscriptionCheckpointRepository checkpointRepository;
  private final EventBus eventBus;
  private EventStoreDBSubscriptionToAllOptions subscriptionOptions;
  private final Object resubscribeLock = new Object();
  private Subscription subscription;
  private boolean isRunning;
  private final Logger logger = LoggerFactory.getLogger(EventStoreDBSubscriptionToAll.class);

  SubscriptionListener listener = new SubscriptionListener() {
    @Override
    public void onEvent(Subscription subscription, ResolvedEvent event) {
      try {
        handleEvent(event);
      } catch (Throwable e) {
        logger.error("Error while handling event", e);
        throw new RuntimeException(e);
      }
    }

    @Override
    public void onError(Subscription subscription, Throwable throwable) {
      handleDrop(throwable);
    }
  };

  public EventStoreDBSubscriptionToAll(
    EventStoreDBClient eventStoreClient,
    SubscriptionCheckpointRepository checkpointRepository,
    EventBus eventBus
  ) {
    this.eventStoreClient = eventStoreClient;
    this.checkpointRepository = checkpointRepository;
    this.eventBus = eventBus;
  }

  public void subscribeToAll() throws ExecutionException, InterruptedException {
    subscribeToAll(EventStoreDBSubscriptionToAllOptions.getDefault());
  }

  public void subscribeToAll(EventStoreDBSubscriptionToAllOptions subscriptionOptions) throws ExecutionException, InterruptedException {
    this.subscriptionOptions = subscriptionOptions;

    var checkpoint = checkpointRepository.load(this.subscriptionOptions.subscriptionId());

    if (!checkpoint.isEmpty()) {
      this.subscriptionOptions.subscribeToAllOptions()
        .fromPosition(new Position(checkpoint.get(), checkpoint.get()));
    } else {
      this.subscriptionOptions.subscribeToAllOptions()
        .fromStart();
    }


    logger.info("Subscribing to all '%s'".formatted(subscriptionOptions.subscriptionId()));

    subscription = eventStoreClient.subscribeToAll(
      listener,
      this.subscriptionOptions.subscribeToAllOptions()
    ).get();

    isRunning = true;
  }

  public void stop() {
    if (!isRunning)
      return;

    isRunning = false;
    subscription.stop();
  }

  public boolean isRunning() {
    return this.isRunning;
  }

  private void handleEvent(ResolvedEvent resolvedEvent) throws ExecutionException, InterruptedException {
    if (isEventWithEmptyData(resolvedEvent) || isCheckpointEvent(resolvedEvent))
      return;

    var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

    var streamEvent = eventClass.flatMap(c -> EventEnvelope.of(c, resolvedEvent));

    if (streamEvent.isEmpty()) {
      // That can happen if we're sharing database between modules.
      // If we're subscribing to all and not filtering out events from other modules,
      // then we might get events that are from other module, and we might not be able to deserialize them.
      // In that case it's safe to ignore deserialization error.
      // You may add more sophisticated logic checking if it should be ignored or not.
      logger.warn("Couldn't deserialize event with id: %s".formatted(resolvedEvent.getEvent().getEventId()));

      if (!subscriptionOptions.ignoreDeserializationErrors())
        throw new IllegalStateException(
          "Unable to deserialize event %s with id: %s"
            .formatted(resolvedEvent.getEvent().getEventType(), resolvedEvent.getEvent().getEventId())
        );

      return;
    }

    // publish event to internal event bus
    eventBus.publish(eventClass.get(), (EventEnvelope<?>) streamEvent.get());

    checkpointRepository.store(
      this.subscriptionOptions.subscriptionId(),
      resolvedEvent.getEvent().getPosition().getCommitUnsigned()
    );
  }

  private void handleDrop(Throwable throwable) {
    logger.error("Subscription was dropped", throwable);

    resubscribe();
  }

  private void resubscribe() {
    // You may consider adding a max resubscribe count if you want to fail process
    // instead of retrying until database is up
    while (true) {
      var resubscribed = false;
      synchronized (resubscribeLock) {
        try {
          subscribeToAll(subscriptionOptions);

          resubscribed = true;
        } catch (Throwable e) {
          logger.error("Failed to resubscribe to all '%s' dropped".formatted(subscriptionOptions.subscriptionId()), e);
        }
      }

      if (resubscribed)
        break;

      // Sleep between reconnections to not flood the database or not kill the CPU with infinite loop
      // Randomness added to reduce the chance of multiple subscriptions trying to reconnect at the same time
      try {
        Thread.sleep(1000 + new Random().nextInt(1000));
      } catch (InterruptedException e) {
        logger.error("Failed to wait to resubscribe", e);
      }
    }
  }

  private boolean isEventWithEmptyData(ResolvedEvent resolvedEvent) {
    if (resolvedEvent.getEvent().getEventData().length != 0) return false;

    logger.info("Event without data received");
    return true;
  }

  private boolean isCheckpointEvent(ResolvedEvent resolvedEvent) {
    if (!resolvedEvent.getEvent().getEventType().equals(EventTypeMapper.toName(CheckpointStored.class)))
      return false;

    logger.info("Checkpoint event - ignoring");
    return true;
  }
}
