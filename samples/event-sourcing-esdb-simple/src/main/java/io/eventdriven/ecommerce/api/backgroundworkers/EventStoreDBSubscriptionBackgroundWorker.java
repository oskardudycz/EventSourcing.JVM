package io.eventdriven.ecommerce.api.backgroundworkers;

import com.eventstore.dbclient.EventStoreDBClient;
import io.eventdriven.ecommerce.core.subscriptions.EventStoreDBSubscriptionToAll;
import org.springframework.context.SmartLifecycle;

public class EventStoreDBSubscriptionBackgroundWorker implements SmartLifecycle {
  private final EventStoreDBClient eventStore;
  private EventStoreDBSubscriptionToAll subscription;

  public EventStoreDBSubscriptionBackgroundWorker(EventStoreDBClient eventStore) {
    this.eventStore = eventStore;
  }

  @Override
  public void start() {
    try {
      subscription = new EventStoreDBSubscriptionToAll(eventStore);
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
