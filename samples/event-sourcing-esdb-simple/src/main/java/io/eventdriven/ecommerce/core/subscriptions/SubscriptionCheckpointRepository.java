package io.eventdriven.ecommerce.core.subscriptions;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public interface SubscriptionCheckpointRepository
{
  Optional<Long> load(String subscriptionId) throws InterruptedException, ExecutionException;

  void store(String subscriptionId, long position) throws ExecutionException, InterruptedException;
}
