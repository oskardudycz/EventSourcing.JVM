package io.eventdriven.uniqueness.core.kurrentdb.subscriptions;

import io.kurrent.dbclient.SubscribeToAllOptions;
import io.kurrent.dbclient.SubscriptionFilter;
import io.eventdriven.uniqueness.core.serialization.EventTypeMapper;

public final class SubscribeToAllOptionsFactory {
  public static <EventType> SubscribeToAllOptions filterByType(Class<EventType> eventType) {
    return SubscribeToAllOptions.get()
      .filter(SubscriptionFilter.newBuilder().addStreamNamePrefix(EventTypeMapper.toName(eventType)).build());
  }
}
