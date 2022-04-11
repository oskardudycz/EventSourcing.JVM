package io.eventdriven.ecommerce.core.subscriptions;

import com.eventstore.dbclient.SubscribeToAllOptions;
import com.eventstore.dbclient.SubscriptionFilter;

record EventStoreDBSubscriptionToAllOptions(
  String subscriptionId,
  boolean ignoreDeserializationErrors,
  SubscribeToAllOptions subscribeToAllOptions
) {
  static EventStoreDBSubscriptionToAllOptions getDefault() {
    SubscriptionFilter filterOutSystemEvents = SubscriptionFilter.newBuilder()
      .withEventTypeRegularExpression("^[^\\$].*")
      .build();

    SubscribeToAllOptions options = SubscribeToAllOptions.get()
      .fromStart()
      .filter(filterOutSystemEvents);

    return new EventStoreDBSubscriptionToAllOptions("default", true, options);
  }
}
