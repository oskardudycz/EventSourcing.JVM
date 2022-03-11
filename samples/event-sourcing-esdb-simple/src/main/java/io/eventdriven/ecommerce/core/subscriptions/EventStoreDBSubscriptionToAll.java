package io.eventdriven.ecommerce.core.subscriptions;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.Subscription;
import com.eventstore.dbclient.SubscriptionListener;
import io.eventdriven.ecommerce.core.events.EventTypeMapper;

import java.util.Random;
import java.util.concurrent.CompletableFuture;


public class EventStoreDBSubscriptionToAll {

  //  private readonly IEventBus eventBus;
  private final EventStoreDBClient eventStoreClient;
  //  private readonly ISubscriptionCheckpointRepository checkpointRepository;
  private EventStoreDBSubscriptionToAllOptions subscriptionOptions;
  private final Object resubscribeLock = new Object();
//  private CancellationToken cancellationToken;

  public EventStoreDBSubscriptionToAll(
    EventStoreDBClient eventStoreClient
//    IEventBus eventBus,
//    ISubscriptionCheckpointRepository checkpointRepository
  ) {
//    this.eventBus = eventBus ?? throw new ArgumentNullException(nameof(eventBus));
    this.eventStoreClient = eventStoreClient;
//    this.checkpointRepository =
//      checkpointRepository ?? throw new ArgumentNullException(nameof(checkpointRepository));
//    this.logger = logger ?? throw new ArgumentNullException(nameof(logger));
  }

  public CompletableFuture<Subscription> subscribeToAll() {
    return subscribeToAll(EventStoreDBSubscriptionToAllOptions.getDefault());
  }

  public CompletableFuture<Subscription> subscribeToAll(EventStoreDBSubscriptionToAllOptions subscriptionOptions) {
    this.subscriptionOptions = subscriptionOptions;

//    var checkpoint = await checkpointRepository.Load(SubscriptionId, ct);

    System.out.println("Subscription to all '%s'".formatted(subscriptionOptions.subscriptionId()));

    SubscriptionListener listener = new SubscriptionListener() {
      @Override
      public void onEvent(Subscription subscription, ResolvedEvent event) {
        handleEvent(subscription, event);
      }

      @Override
      public void onError(Subscription subscription, Throwable throwable) {
        System.out.println("Subscription was dropped due to " + throwable.getMessage());
        handleDrop(subscription, throwable);
      }
    };

    return eventStoreClient.subscribeToAll(
      listener,
      this.subscriptionOptions.subscribeToAllOptions()
    );
  }

  private void handleEvent(Subscription subscription, ResolvedEvent resolvedEvent) {
    try {
      if (isEventWithEmptyData(resolvedEvent) || isCheckpointEvent(resolvedEvent))
        return;

//      var streamEvent = resolvedEvent.ToStreamEvent();
//
//      if (streamEvent == null) {
//        // That can happen if we're sharing database between modules.
//        // If we're subscribing to all and not filtering out events from other modules,
//        // then we might get events that are from other module and we might not be able to deserialize them.
//        // In that case it's safe to ignore deserialization error.
//        // You may add more sophisticated logic checking if it should be ignored or not.
//        System.out.println("Couldn't deserialize event with id: %s".formatted(resolvedEvent.getEvent().getEventId()));
//
//        if (!subscriptionOptions.ignoreDeserializationErrors())
//          throw new IllegalStateException(
//            "Unable to deserialize event %s with id: %s"
//              .formatted(resolvedEvent.getEvent().getEventType(), resolvedEvent.getEvent().getEventId())
//          );
//
//        return;
//      }

      // publish event to internal event bus
//      await eventBus.Publish(streamEvent, ct);
//
//      await checkpointRepository.Store(SubscriptionId, resolvedEvent.Event.Position.CommitPosition, ct);
    } catch (Exception e) {
      System.out.println("Error consuming message: {%s}".formatted(e.getMessage()));
      e.printStackTrace();
      // if you're fine with dropping some events instead of stopping subscription
      // then you can add some logic if error should be ignored
      throw e;
    }
  }

  private void handleDrop(Subscription subscription, Throwable throwable) {
    System.out.println("Subscription was dropped due to " + throwable.getMessage());

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
        } catch (Exception exception) {
          System.out.println("Failed to resubscribe to all '%s' dropped with '%s'".formatted(
            subscriptionOptions.subscriptionId(), exception.getMessage())
          );

          exception.printStackTrace();
        }
      }

      if (resubscribed)
        break;

      // Sleep between reconnections to not flood the database or not kill the CPU with infinite loop
      // Randomness added to reduce the chance of multiple subscriptions trying to reconnect at the same time
      try {
        Thread.sleep(1000 + new Random().nextInt(1000));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private static boolean isEventWithEmptyData(ResolvedEvent resolvedEvent) {
    if (resolvedEvent.getEvent().getEventData().length != 0) return false;

    System.out.println("Event without data received");
    return true;
  }

  private static boolean isCheckpointEvent(ResolvedEvent resolvedEvent) {
    if (resolvedEvent.getEvent().getEventType() != EventTypeMapper.ToName(CheckpointStored.class))
      return false;

    System.out.println("Checkpoint event - ignoring");
    return true;
  }
}