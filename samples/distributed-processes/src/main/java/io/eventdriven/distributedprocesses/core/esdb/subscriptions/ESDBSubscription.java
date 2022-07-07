package io.eventdriven.distributedprocesses.core.esdb.subscriptions;

import com.eventstore.dbclient.*;

import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public final class ESDBSubscription {
  public static Subscription subscribeToAll(
    EventStoreDBClient eventStore,
    BiConsumer<Subscription, ResolvedEvent> handle
  ) {
    return subscribeToAll(eventStore, SubscribeToAllOptions.get(), handle);
  }

  public static Subscription subscribeToAll(
    EventStoreDBClient eventStore,
    SubscribeToAllOptions options,
    BiConsumer<Subscription, ResolvedEvent> handle
  ) {
    try {
      // Note this is a pretty naive version of subscription handling.
      // It doesn't have error handling, retries and resubscribes.
      // For the full solution, check main samples.
      return eventStore.subscribeToAll(new SubscriptionListener() {
        @Override
        public void onEvent(Subscription subscription, ResolvedEvent resolvedEvent) {
          handle.accept(subscription, resolvedEvent);
        }
      }, options).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public static Subscription subscribeToStream(
    EventStoreDBClient eventStore,
    String streamId,
    BiConsumer<Subscription, ResolvedEvent> handle
  ) {
    try {
      // Note this is a pretty naive version of subscription handling.
      // It doesn't have error handling, retries and resubscribes.
      // For the full solution, check main samples.
      return eventStore.subscribeToStream(streamId, new SubscriptionListener() {
        @Override
        public void onEvent(Subscription subscription, ResolvedEvent resolvedEvent) {
          handle.accept(subscription, resolvedEvent);
        }
      }).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
