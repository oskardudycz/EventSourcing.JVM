package io.eventdriven.ecommerce.api.backgroundworkers;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.subscriptions.EventStoreDBSubscriptionToAll;
import io.eventdriven.ecommerce.core.subscriptions.SubscriptionCheckpointRepository;
import org.springframework.context.SmartLifecycle;

public class EventStoreDBSubscriptionBackgroundWorker implements SmartLifecycle {
  private final SubscriptionCheckpointRepository subscriptionCheckpointRepository;
  private final EventStoreDBClient eventStore;
  private final EventBus eventBus;
  private EventStoreDBSubscriptionToAll subscription;

  public EventStoreDBSubscriptionBackgroundWorker(
    EventStoreDBClient eventStore,
    SubscriptionCheckpointRepository subscriptionCheckpointRepository,
    EventBus eventBus
  ) {
    this.eventStore = eventStore;
    this.subscriptionCheckpointRepository = subscriptionCheckpointRepository;
    this.eventBus = eventBus;
  }

  @Override
  public void start() {
    try {
      subscription = new EventStoreDBSubscriptionToAll(
        eventStore,
        subscriptionCheckpointRepository,
        eventBus
      );
      subscription.subscribeToAll();
    } catch (Throwable e) {
      System.out.println("Failed to start Subscription to All");
      e.printStackTrace();
    }
  }

  @Override
  public void stop() {
    stop(null);
  }

  @Override
  public boolean isRunning() {
    return subscription != null && subscription.isRunning();
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    if (!isRunning()) return;

    subscription.stop();

    if (callback != null) {
      callback.run();
    }
  }

}
